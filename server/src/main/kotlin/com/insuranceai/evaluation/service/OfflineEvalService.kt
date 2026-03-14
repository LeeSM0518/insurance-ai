package com.insuranceai.evaluation.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.service.ChatPromptBuilder
import com.insuranceai.chat.service.ContextBuilder
import com.insuranceai.chat.service.QuestionAnalyzer
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.domain.EvalType
import com.insuranceai.evaluation.domain.QaPairStatus
import com.insuranceai.evaluation.domain.QuestionType
import com.insuranceai.evaluation.entity.GoldenEvalResultEntity
import com.insuranceai.evaluation.repository.GoldenEvalResultRepository
import com.insuranceai.evaluation.repository.GoldenQaPairRepository
import com.insuranceai.evaluation.repository.GoldenQaSourceRepository
import com.insuranceai.search.service.SearchService
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class EvalReport(
    val evalRunId: String,
    val datasetName: String,
    val totalCount: Int,
    // 검색 지표
    val avgContextPrecision: BigDecimal,
    val avgContextRecall: BigDecimal,
    val avgMrr: BigDecimal,
    val avgNdcg: BigDecimal,
    // 생성 지표
    val mustIncludePassCount: Int,
    val mustNotIncludePassCount: Int,
    val sourceMatchPassCount: Int,
    // 리스크별
    val riskBreakdown: Map<String, Pair<Int, Int>>,
    // 실패 케이스
    val failedCases: List<String>,
    // 거절 능력
    val rejectionAccuracy: Int,
    val rejectionTotal: Int,
    val overRejectionCount: Int,
    val overRejectionTotal: Int,
)

@Service
class OfflineEvalService(
    private val qaPairRepository: GoldenQaPairRepository,
    private val qaSourceRepository: GoldenQaSourceRepository,
    private val evalResultRepository: GoldenEvalResultRepository,
    private val searchService: SearchService,
    private val questionAnalyzer: QuestionAnalyzer,
    private val contextBuilder: ContextBuilder,
    private val chatPromptBuilder: ChatPromptBuilder,
    private val suspendingChatClient: SuspendingChatClient,
    private val ruleBasedEvalService: RuleBasedEvalService,
    private val searchMetricsCalculator: SearchMetricsCalculator,
    private val goldenDatasetService: GoldenDatasetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 오프라인 평가 실행 */
    suspend fun evaluate(datasetId: UUID): EvalReport {
        val dataset = goldenDatasetService.getDataset(datasetId)
        val qaPairs = qaPairRepository.findByDatasetIdAndStatus(datasetId, QaPairStatus.GOLD.name).toList()

        val evalRunId = "EVAL-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}"
        log.info("오프라인 평가 시작: {} ({}개 QA 쌍, runId={})", dataset.name, qaPairs.size, evalRunId)

        val failedCases = mutableListOf<String>()
        val riskBreakdown = mutableMapOf<String, Pair<Int, Int>>() // riskLevel → (passed, total)

        var rejectionCorrect = 0
        var rejectionTotal = 0
        var overRejection = 0
        var nonRejectionTotal = 0

        for (pair in qaPairs) {
            try {
                val sources = qaSourceRepository.findByQaPairId(pair.id!!).toList()
                val isRejection = pair.questionType == QuestionType.REJECTION.name

                if (isRejection) {
                    rejectionTotal++
                    // 거절 질문: AI가 적절히 거절하는지 확인
                    val analysis = questionAnalyzer.analyze(pair.question)
                    val actualAnswer = generateAnswer(pair.question, analysis)

                    val isRejected = isRejectionResponse(actualAnswer)
                    if (isRejected) rejectionCorrect++

                    evalResultRepository.save(
                        GoldenEvalResultEntity(
                            datasetId = datasetId,
                            qaPairId = pair.id,
                            evalRunId = evalRunId,
                            evalType = EvalType.RULE_BASED.name,
                            actualAnswer = actualAnswer,
                            mustIncludePass = isRejected,
                            mustNotIncludePass = true,
                            sourceMatchPass = true,
                            modelVersion = "claude-sonnet-4-6",
                        ),
                    )
                    continue
                }

                nonRejectionTotal++

                // 1단계: 검색 실행
                val analysis = questionAnalyzer.analyze(pair.question)
                val searchResults = searchService.searchForRag(pair.question, analysis)
                val retrievedArticleIds = searchResults.map { it.articleId }

                // 검색 지표 계산
                val groundTruthIds = sources.map { it.policyArticleId }.toSet()
                val searchMetrics = searchMetricsCalculator.calculate(retrievedArticleIds, groundTruthIds)

                // 2단계: 답변 생성
                val actualAnswer = generateAnswer(pair.question, analysis)

                // 과잉 거절 체크
                if (isRejectionResponse(actualAnswer)) {
                    overRejection++
                }

                // 3단계: 규칙 기반 평가
                val ruleResult = ruleBasedEvalService.evaluate(pair, actualAnswer, retrievedArticleIds, sources)

                // 결과 저장
                evalResultRepository.save(
                    GoldenEvalResultEntity(
                        datasetId = datasetId,
                        qaPairId = pair.id,
                        evalRunId = evalRunId,
                        evalType = EvalType.RULE_BASED.name,
                        retrievedArticleIds = retrievedArticleIds,
                        contextPrecision = searchMetrics.contextPrecision,
                        contextRecall = searchMetrics.contextRecall,
                        mrr = searchMetrics.mrr,
                        ndcg = searchMetrics.ndcg,
                        mustIncludePass = ruleResult.mustIncludePass,
                        mustNotIncludePass = ruleResult.mustNotIncludePass,
                        sourceMatchPass = ruleResult.sourceMatchPass,
                        actualAnswer = actualAnswer,
                        modelVersion = "claude-sonnet-4-6",
                    ),
                )

                // 실패 케이스 기록
                val allPassed = ruleResult.mustIncludePass && ruleResult.mustNotIncludePass && ruleResult.sourceMatchPass
                val riskPair = riskBreakdown.getOrDefault(pair.riskLevel, 0 to 0)
                riskBreakdown[pair.riskLevel] =
                    (riskPair.first + if (allPassed) 1 else 0) to (riskPair.second + 1)

                if (!allPassed) {
                    val details = mutableListOf<String>()
                    if (!ruleResult.mustIncludePass) details.addAll(ruleResult.mustIncludeDetails)
                    if (!ruleResult.mustNotIncludePass) details.addAll(ruleResult.mustNotIncludeDetails)
                    if (!ruleResult.sourceMatchPass) details.addAll(ruleResult.sourceMatchDetails)
                    failedCases.add("${pair.qaCode}: ${details.joinToString(", ")}")
                }
            } catch (e: Exception) {
                log.error("평가 실패: {} — {}", pair.qaCode, e.message)
                failedCases.add("${pair.qaCode}: 평가 실행 에러 — ${e.message}")
            }
        }

        // 집계
        val results = evalResultRepository.findByEvalRunId(evalRunId).toList()
        val nonRejectionResults = results.filter { it.contextPrecision != null }

        val report =
            EvalReport(
                evalRunId = evalRunId,
                datasetName = "${dataset.name} v${dataset.version}",
                totalCount = qaPairs.size,
                avgContextPrecision = avg(nonRejectionResults.mapNotNull { it.contextPrecision }),
                avgContextRecall = avg(nonRejectionResults.mapNotNull { it.contextRecall }),
                avgMrr = avg(nonRejectionResults.mapNotNull { it.mrr }),
                avgNdcg = avg(nonRejectionResults.mapNotNull { it.ndcg }),
                mustIncludePassCount = results.count { it.mustIncludePass == true },
                mustNotIncludePassCount = results.count { it.mustNotIncludePass == true },
                sourceMatchPassCount = results.count { it.sourceMatchPass == true },
                riskBreakdown = riskBreakdown,
                failedCases = failedCases,
                rejectionAccuracy = rejectionCorrect,
                rejectionTotal = rejectionTotal,
                overRejectionCount = overRejection,
                overRejectionTotal = nonRejectionTotal,
            )

        log.info("오프라인 평가 완료: {}", evalRunId)
        return report
    }

    /** AI 답변 생성 */
    private suspend fun generateAnswer(
        question: String,
        analysis: QuestionAnalysis,
    ): String {
        val searchResults = searchService.searchForRag(question, analysis)
        val contextResult = contextBuilder.build(searchResults, analysis, emptyList())
        val chatPrompt = chatPromptBuilder.build(question, contextResult, analysis.isComparison)
        return suspendingChatClient.call(
            userMessage = chatPrompt.userPrompt,
            systemMessage = chatPrompt.systemPrompt,
        )
    }

    /** 거절 응답 판별 */
    private fun isRejectionResponse(answer: String): Boolean {
        val rejectionKeywords = listOf("죄송합니다", "답변드릴 수 없습니다", "할 수 없습니다", "제공해 드릴 수 없습니다")
        return rejectionKeywords.any { answer.contains(it) }
    }

    /** BigDecimal 평균 */
    private fun avg(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val sum = values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        return sum.divide(BigDecimal(values.size), 4, RoundingMode.HALF_UP)
    }

    /** 리포트 콘솔 출력 */
    fun printReport(report: EvalReport) {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════════")
        sb.appendLine("  골든 데이터셋 오프라인 평가 리포트")
        sb.appendLine("  실행 ID: ${report.evalRunId}")
        sb.appendLine("  데이터셋: ${report.datasetName} (${report.totalCount}개 QA Pair)")
        sb.appendLine("  모델: claude-sonnet-4-6")
        sb.appendLine("═══════════════════════════════════════════════")
        sb.appendLine()
        sb.appendLine("[검색 품질]")
        sb.appendLine(
            "  Context Precision@10: ${report.avgContextPrecision} (목표: ≥0.80) ${if (report.avgContextPrecision >=
                BigDecimal(
                    "0.80",
                )
            ) {
                "✅"
            } else {
                "⚠️"
            }}",
        )
        sb.appendLine(
            "  Context Recall:       ${report.avgContextRecall} (목표: ≥0.85) ${if (report.avgContextRecall >=
                BigDecimal(
                    "0.85",
                )
            ) {
                "✅"
            } else {
                "⚠️"
            }}",
        )
        sb.appendLine("  MRR:                  ${report.avgMrr} (목표: ≥0.75) ${if (report.avgMrr >= BigDecimal("0.75")) "✅" else "⚠️"}")
        sb.appendLine("  NDCG@10:              ${report.avgNdcg} (목표: ≥0.80) ${if (report.avgNdcg >= BigDecimal("0.80")) "✅" else "⚠️"}")
        sb.appendLine()
        sb.appendLine("[생성 품질 — 규칙 기반]")
        sb.appendLine("  must_include 통과율:     ${report.mustIncludePassCount}/${report.totalCount}")
        sb.appendLine("  must_not_include 통과율: ${report.mustNotIncludePassCount}/${report.totalCount}")
        sb.appendLine("  Source Accuracy:         ${report.sourceMatchPassCount}/${report.totalCount}")
        sb.appendLine()
        sb.appendLine("[리스크별 성적]")
        for ((risk, counts) in report.riskBreakdown) {
            val (passed, total) = counts
            val pct = if (total > 0) "%.1f".format(passed.toDouble() / total * 100) else "N/A"
            sb.appendLine("  $risk (${total}건): 통과 $passed/$total ($pct%)")
        }
        sb.appendLine()
        if (report.failedCases.isNotEmpty()) {
            sb.appendLine("[실패 케이스 상세]")
            for (case in report.failedCases.take(20)) {
                sb.appendLine("  $case")
            }
            sb.appendLine()
        }
        sb.appendLine("[거절 능력]")
        sb.appendLine("  거절 정확도: ${report.rejectionAccuracy}/${report.rejectionTotal}")
        sb.appendLine("  과잉 거절률: ${report.overRejectionCount}/${report.overRejectionTotal}")

        log.info("\n{}", sb.toString())
    }
}
