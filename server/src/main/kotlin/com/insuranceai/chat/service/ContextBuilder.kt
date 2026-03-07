package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import com.insuranceai.search.dto.FusedSearchResult
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.util.UUID

data class ContextResult(
    val contextText: String,
    val sources: List<SourceInfo>,
    val tokenEstimate: Int,
)

@Service
class ContextBuilder(
    private val surrenderValueRepository: SurrenderValueRepository,
    private val premiumExampleRepository: PremiumExampleRepository,
    private val productIndexRepository: ProductIndexRepository,
) {
    companion object {
        const val MAX_ARTICLES = 10
        const val MAX_MESSAGES = 20
        const val TOKEN_BUDGET = 16_500
        const val ARTICLE_BUDGET = 5_000
        const val SUPPLEMENTARY_BUDGET = 2_000
        const val HISTORY_BUDGET = 5_000
    }

    /** 검색 결과 + 보충 데이터 → 컨텍스트 문자열 조합 */
    suspend fun build(
        searchResults: List<FusedSearchResult>,
        analysis: QuestionAnalysis,
        conversationHistory: List<ChatMessageEntity>,
    ): ContextResult {
        val sources = mutableListOf<SourceInfo>()
        val contextParts = mutableListOf<String>()
        var totalTokens = 0

        // 1. 검색된 약관 자료
        val articlesSection = buildArticlesSection(searchResults.take(MAX_ARTICLES), sources)
        val articlesTokens = estimateTokens(articlesSection)
        if (articlesTokens <= ARTICLE_BUDGET) {
            contextParts.add(articlesSection)
            totalTokens += articlesTokens
        } else {
            // 토큰 예산 초과 시 낮은 유사도 조문부터 제거
            val truncated = truncateArticles(searchResults, sources, ARTICLE_BUDGET)
            contextParts.add(truncated)
            totalTokens += estimateTokens(truncated)
        }

        // 2. 보충 정형 데이터
        val productIds = searchResults.take(MAX_ARTICLES).map { it.productId }.distinct()
        val supplementarySection = buildSupplementarySection(productIds, analysis)
        val suppTokens = estimateTokens(supplementarySection)
        if (supplementarySection.isNotBlank() && totalTokens + suppTokens <= TOKEN_BUDGET) {
            if (suppTokens <= SUPPLEMENTARY_BUDGET) {
                contextParts.add(supplementarySection)
                totalTokens += suppTokens
            } else {
                val truncated = supplementarySection.take((SUPPLEMENTARY_BUDGET / 1.5).toInt())
                contextParts.add(truncated)
                totalTokens += estimateTokens(truncated)
            }
        }

        // 3. 대화 이력
        val historySection = buildHistorySection(conversationHistory)
        val historyTokens = estimateTokens(historySection)
        if (historySection.isNotBlank() && totalTokens + historyTokens <= TOKEN_BUDGET) {
            if (historyTokens <= HISTORY_BUDGET) {
                contextParts.add(historySection)
                totalTokens += historyTokens
            } else {
                // 오래된 메시지부터 제거
                val truncated = truncateHistory(conversationHistory, HISTORY_BUDGET)
                contextParts.add(truncated)
                totalTokens += estimateTokens(truncated)
            }
        }

        return ContextResult(
            contextText = contextParts.joinToString("\n\n"),
            sources = sources,
            tokenEstimate = totalTokens,
        )
    }

    /** 검색된 약관 자료 섹션 구성 */
    private fun buildArticlesSection(
        results: List<FusedSearchResult>,
        sources: MutableList<SourceInfo>,
    ): String {
        if (results.isEmpty()) return ""

        val sb = StringBuilder("## 검색된 약관 자료\n\n")
        results.forEachIndexed { index, result ->
            val sourceNum = index + 1
            val articleRef =
                if (result.articleNumber != null) {
                    "${result.articleNumber} (${result.articleTitle ?: ""})"
                } else {
                    result.articleTitle ?: ""
                }

            sb.appendLine("### [출처 $sourceNum] ${result.companyName} - ${result.productName} - $articleRef")
            if (result.dataSource != null) {
                sb.appendLine("데이터 출처: ${result.dataSource}")
            }
            sb.appendLine(result.content)
            sb.appendLine()

            sources.add(
                SourceInfo(
                    articleId = result.articleId,
                    insuranceCompany = result.companyName,
                    productId = result.productId,
                    productName = result.productName,
                    articleNumber = result.articleNumber,
                    articleTitle = result.articleTitle,
                    relevanceScore = result.relevanceScore,
                    dataSource = result.dataSource ?: "",
                ),
            )
        }
        return sb.toString().trimEnd()
    }

    /** 약관 자료 토큰 예산 초과 시 하위 유사도 결과 제거 */
    private fun truncateArticles(
        results: List<FusedSearchResult>,
        sources: MutableList<SourceInfo>,
        budget: Int,
    ): String {
        sources.clear()
        for (count in MAX_ARTICLES downTo 1) {
            val section = buildArticlesSection(results.take(count), sources)
            if (estimateTokens(section) <= budget) return section
            sources.clear()
        }
        return buildArticlesSection(results.take(1), sources)
    }

    /** 보충 정형 데이터 섹션 구성 */
    private suspend fun buildSupplementarySection(
        productIds: List<UUID>,
        analysis: QuestionAnalysis,
    ): String {
        if (productIds.isEmpty()) return ""
        val needs = analysis.needsSupplementary
        if (!needs.surrenderValue && !needs.premiumExample && !needs.priceIndex) return ""

        val sb = StringBuilder("## 보충 정형 데이터\n\n")

        if (needs.surrenderValue) {
            for (productId in productIds) {
                val values = surrenderValueRepository.findByProductId(productId).toList()
                if (values.isNotEmpty()) {
                    sb.appendLine("### 해약환급금")
                    sb.appendLine("| 경과월수 | 납입보험료 | 해약환급금 | 환급률 |")
                    sb.appendLine("|---------|----------|----------|-------|")
                    values.forEach { v ->
                        sb.appendLine("| ${v.elapsedMonths} | ${v.premiumPaid} | ${v.surrenderValue} | ${v.surrenderRate}% |")
                    }
                    sb.appendLine()
                }
            }
        }

        if (needs.premiumExample) {
            for (productId in productIds) {
                val examples = premiumExampleRepository.findByProductId(productId).toList()
                if (examples.isNotEmpty()) {
                    sb.appendLine("### 보험료 예시")
                    sb.appendLine("| 성별 | 나이 | 보험기간 | 납입기간 | 보험가입금액 | 보험료 |")
                    sb.appendLine("|------|------|---------|---------|-----------|-------|")
                    examples.forEach { e ->
                        sb.appendLine(
                            "| ${e.gender} | ${e.age} | ${e.insurancePeriod} | ${e.paymentPeriod} | ${e.insuranceAmount} | ${e.premium} |",
                        )
                    }
                    sb.appendLine()
                }
            }
        }

        if (needs.priceIndex) {
            for (productId in productIds) {
                val indices = productIndexRepository.findByProductId(productId).toList()
                if (indices.isNotEmpty()) {
                    sb.appendLine("### 가격지수")
                    sb.appendLine("| 지수유형 | 성별 | 담보명 | 지수값 | 기준일 |")
                    sb.appendLine("|---------|------|-------|-------|-------|")
                    indices.forEach { idx ->
                        sb.appendLine(
                            "| ${idx.indexType} | ${idx.gender ?: "-"} | ${idx.coverageName ?: "-"} | ${idx.indexValue} | ${idx.baseDate} |",
                        )
                    }
                    sb.appendLine()
                }
            }
        }

        val result = sb.toString().trimEnd()
        return if (result == "## 보충 정형 데이터") "" else result
    }

    /** 대화 이력 섹션 구성 */
    private fun buildHistorySection(messages: List<ChatMessageEntity>): String {
        if (messages.isEmpty()) return ""

        val recentMessages = messages.takeLast(MAX_MESSAGES)
        val sb = StringBuilder("## 대화 이력\n\n")
        recentMessages.forEach { msg ->
            val role = msg.role.uppercase()
            sb.appendLine("$role: ${msg.content}")
        }
        return sb.toString().trimEnd()
    }

    /** 대화 이력 토큰 예산 초과 시 오래된 메시지 제거 */
    private fun truncateHistory(
        messages: List<ChatMessageEntity>,
        budget: Int,
    ): String {
        for (count in messages.size.coerceAtMost(MAX_MESSAGES) downTo 2) {
            val section = buildHistorySection(messages.takeLast(count))
            if (estimateTokens(section) <= budget) return section
        }
        return buildHistorySection(messages.takeLast(2))
    }

    /** 토큰 추정: 한글 기준 글자 수 * 1.5 */
    internal fun estimateTokens(text: String): Int = (text.length * 1.5).toInt()
}
