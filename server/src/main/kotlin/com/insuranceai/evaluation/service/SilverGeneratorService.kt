package com.insuranceai.evaluation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.domain.Difficulty
import com.insuranceai.evaluation.domain.QaPairStatus
import com.insuranceai.evaluation.domain.QuestionType
import com.insuranceai.evaluation.domain.RiskLevel
import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import com.insuranceai.evaluation.entity.GoldenQaSourceEntity
import com.insuranceai.evaluation.repository.GoldenQaPairRepository
import com.insuranceai.evaluation.repository.GoldenQaSourceRepository
import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.ProductRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/** LLM 응답 파싱용 DTO */
data class GeneratedQaPair(
    val question: String,
    val expected_answer: String,
    val must_include: List<String> = emptyList(),
    val must_not_include: List<String> = emptyList(),
    val ground_truth_sources: List<GeneratedSource> = emptyList(),
)

data class GeneratedSource(
    val article_number: String? = null,
    val content_type: String? = null,
)

@Service
class SilverGeneratorService(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
    private val objectMapper: ObjectMapper,
    private val qaPairRepository: GoldenQaPairRepository,
    private val qaSourceRepository: GoldenQaSourceRepository,
    private val policyArticleRepository: PolicyArticleRepository,
    private val productRepository: ProductRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 배치 분배 계획 */
    data class GenerationPlan(
        val category: ContentType,
        val count: Int,
        val questionTypes: List<QuestionType>,
        val riskLevels: List<RiskLevel>,
    )

    /** 카테고리별 생성 계획 (계획서 §4.2 기반) */
    private val generationPlans =
        listOf(
            GenerationPlan(
                ContentType.EXEMPTION,
                15,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.CONDITIONAL),
                listOf(RiskLevel.CRITICAL, RiskLevel.HIGH),
            ),
            GenerationPlan(
                ContentType.COVERAGE,
                15,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.COMPARISON),
                listOf(RiskLevel.CRITICAL, RiskLevel.HIGH),
            ),
            GenerationPlan(
                ContentType.COVERAGE_DETAIL,
                12,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.COMPLEX),
                listOf(RiskLevel.HIGH, RiskLevel.MEDIUM),
            ),
            GenerationPlan(
                ContentType.REDUCTION,
                10,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.CONDITIONAL),
                listOf(RiskLevel.CRITICAL, RiskLevel.HIGH),
            ),
            GenerationPlan(
                ContentType.QUALIFICATION,
                10,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.CONDITIONAL),
                listOf(RiskLevel.MEDIUM),
            ),
            GenerationPlan(
                ContentType.SPECIAL_TERMS,
                10,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.COMPLEX),
                listOf(RiskLevel.HIGH, RiskLevel.MEDIUM),
            ),
            GenerationPlan(ContentType.CLAIM_DOCUMENTS, 8, listOf(QuestionType.SINGLE_LOOKUP), listOf(RiskLevel.MEDIUM, RiskLevel.LOW)),
            GenerationPlan(ContentType.ARTICLE, 5, listOf(QuestionType.SINGLE_LOOKUP), listOf(RiskLevel.LOW)),
            GenerationPlan(ContentType.QNA, 5, listOf(QuestionType.SINGLE_LOOKUP), listOf(RiskLevel.LOW)),
        )

    /** Silver 데이터셋 생성 (90개 LLM 생성) */
    suspend fun generateSilverDataset(
        datasetId: UUID,
        targetCount: Int = 90,
    ): Int {
        var totalGenerated = 0
        var qaCodeCounter = 1

        for (plan in generationPlans) {
            val adjustedCount = (plan.count * targetCount) / 90

            log.info("카테고리 {} 생성 시작 ({}개)", plan.category, adjustedCount)

            // 해당 카테고리의 약관 조문 조회
            val articles = findArticlesByContentType(plan.category)
            if (articles.isEmpty()) {
                log.warn("카테고리 {}에 해당하는 약관 데이터가 없습니다", plan.category)
                continue
            }

            var generated = 0
            var articleIndex = 0

            while (generated < adjustedCount && articleIndex < articles.size * 3) {
                val article = articles[articleIndex % articles.size]
                val questionType = plan.questionTypes[generated % plan.questionTypes.size]
                val riskLevel = plan.riskLevels[generated % plan.riskLevels.size]
                val difficulty = determineDifficulty(questionType, riskLevel)

                try {
                    val qaCode = "GD-%03d".format(qaCodeCounter)

                    val result =
                        generateSingleQaPair(
                            article = article,
                            category = plan.category,
                            questionType = questionType,
                            difficulty = difficulty,
                            riskLevel = riskLevel,
                        )

                    if (result != null) {
                        saveQaPair(
                            datasetId = datasetId,
                            qaCode = qaCode,
                            article = article,
                            category = plan.category,
                            questionType = questionType,
                            difficulty = difficulty,
                            riskLevel = riskLevel,
                            generated = result,
                        )
                        generated++
                        qaCodeCounter++
                        totalGenerated++
                        log.debug("생성 완료: {} (카테고리: {})", qaCode, plan.category)
                    }
                } catch (e: Exception) {
                    log.warn("QA 생성 실패 (카테고리: {}, 시도: {}): {}", plan.category, articleIndex, e.message)
                }

                articleIndex++
                // API 호출 간 딜레이 (rate limit 방지)
                delay(500)
            }

            log.info("카테고리 {} 생성 완료: {}개", plan.category, generated)
        }

        log.info("Silver 데이터셋 생성 완료: 총 {}개", totalGenerated)
        return totalGenerated
    }

    /** 거절 대상 QA 10개 수동 생성 */
    suspend fun generateRejectionQaPairs(datasetId: UUID): Int {
        val rejections =
            listOf(
                Triple(
                    "오늘 날씨 어때?",
                    "죄송합니다. 저는 보험 약관 검색·분석 AI 어시스턴트입니다. 날씨와 관련된 질문에는 답변드릴 수 없습니다. 보험 약관에 대해 궁금한 점이 있으시면 질문해 주세요.",
                    listOf("보험 약관", "답변드릴 수 없습니다"),
                ),
                Triple(
                    "보험이란 뭔가요?",
                    "죄송합니다. 보험의 일반적인 정의는 약관 자료에 근거한 답변 범위에 포함되지 않습니다. 특정 보험 상품의 약관 내용에 대해 질문해 주시면 정확한 답변을 드리겠습니다.",
                    listOf("약관 자료", "특정 보험 상품"),
                ),
                Triple("알리안츠생명 암보험 보장내용", "죄송합니다. '알리안츠생명'은 현재 등록된 보험사 목록에 포함되어 있지 않습니다. 등록된 보험사의 상품에 대해 질문해 주세요.", listOf("등록된 보험사")),
                Triple("삼성생명 우주보험 면책기간", "죄송합니다. '우주보험'이라는 상품을 찾을 수 없습니다. 정확한 상품명으로 다시 질문해 주시면 답변드리겠습니다.", listOf("찾을 수 없습니다")),
                Triple(
                    "가장 좋은 암보험 추천해줘",
                    "죄송합니다. 저는 보험 상품 추천이나 가입 권유를 할 수 없습니다. 특정 상품의 약관 내용에 대한 객관적인 정보를 제공해 드릴 수 있습니다.",
                    listOf("추천", "할 수 없습니다"),
                ),
                Triple(
                    "이 보험 가입해야 할까요?",
                    "죄송합니다. 보험 가입 여부에 대한 판단은 제공해 드릴 수 없습니다. 약관의 보장 내용, 면책 사항 등 객관적인 정보를 통해 판단에 도움을 드릴 수 있습니다.",
                    listOf("판단", "제공해 드릴 수 없습니다"),
                ),
                Triple(
                    "저한테 이 보험 충분할까요?",
                    "죄송합니다. 개인의 보장 충분성에 대한 평가는 제공해 드릴 수 없습니다. 약관에 명시된 보장 범위와 한도에 대해 안내해 드릴 수 있습니다.",
                    listOf("제공해 드릴 수 없습니다", "보장 범위"),
                ),
                Triple(
                    "지금 보험 해지하고 이걸로 바꿔야 할까요?",
                    "죄송합니다. 보험 해지나 전환(승환)에 대한 권유는 할 수 없습니다. 각 상품의 약관 내용을 비교해 드릴 수는 있습니다.",
                    listOf("할 수 없습니다", "비교"),
                ),
                Triple(
                    "어떤 보험사 수수료가 높아요?",
                    "죄송합니다. 보험사별 수수료 정보는 약관에 포함된 내용이 아니므로 답변드릴 수 없습니다. 약관에 명시된 보장 내용이나 보험료에 대해 질문해 주세요.",
                    listOf("답변드릴 수 없습니다"),
                ),
                Triple(
                    "삼성생명 주가가 올라갈까요?",
                    "죄송합니다. 주가 예측이나 투자 관련 질문에는 답변드릴 수 없습니다. 보험 약관에 대한 질문을 해주시면 도움드리겠습니다.",
                    listOf("답변드릴 수 없습니다", "보험 약관"),
                ),
            )

        val existingCount = qaPairRepository.countByDatasetId(datasetId)
        var qaCodeCounter = existingCount.toInt() + 1

        for ((question, answer, mustInclude) in rejections) {
            val qaCode = "GD-%03d".format(qaCodeCounter)

            qaPairRepository.save(
                GoldenQaPairEntity(
                    datasetId = datasetId,
                    qaCode = qaCode,
                    category = ContentType.ARTICLE.name,
                    questionType = QuestionType.REJECTION.name,
                    difficulty = Difficulty.EASY.name,
                    riskLevel = RiskLevel.HIGH.name,
                    question = question,
                    expectedAnswer = answer,
                    mustInclude = mustInclude,
                    mustNotInclude = listOf("추측", "일반적으로", "보통"),
                    sourceMustMatch = false,
                    status = QaPairStatus.GOLD.name,
                ),
            )
            qaCodeCounter++
        }

        log.info("거절 대상 QA {}개 생성 완료", rejections.size)
        return rejections.size
    }

    /** 단일 QA 쌍 LLM 생성 */
    private suspend fun generateSingleQaPair(
        article: ArticleWithContext,
        category: ContentType,
        questionType: QuestionType,
        difficulty: Difficulty,
        riskLevel: RiskLevel,
    ): GeneratedQaPair? {
        val prompt =
            promptLoader.render(
                "evaluation/silver-generation.st",
                mapOf(
                    "insuranceCompany" to article.companyName,
                    "productName" to article.productName,
                    "articleContents" to article.content,
                    "category" to category.name,
                    "difficulty" to difficulty.name,
                    "questionType" to questionType.name,
                    "riskLevel" to riskLevel.name,
                ),
            )

        val response = suspendingChatClient.call(userMessage = prompt)

        return parseQaPairResponse(response)
    }

    /** LLM 응답 JSON 파싱 */
    private fun parseQaPairResponse(response: String): GeneratedQaPair? =
        try {
            // JSON 블록 추출 (```json ... ``` 또는 직접 JSON)
            val jsonStr =
                if (response.contains("```")) {
                    response
                        .substringAfter("```json", response.substringAfter("```"))
                        .substringBefore("```")
                        .trim()
                } else {
                    response.trim()
                }

            objectMapper.readValue<GeneratedQaPair>(jsonStr)
        } catch (e: Exception) {
            log.warn("QA 응답 파싱 실패: {}", e.message)
            null
        }

    /** QA 쌍 저장 */
    private suspend fun saveQaPair(
        datasetId: UUID,
        qaCode: String,
        article: ArticleWithContext,
        category: ContentType,
        questionType: QuestionType,
        difficulty: Difficulty,
        riskLevel: RiskLevel,
        generated: GeneratedQaPair,
    ) {
        val qaPair =
            qaPairRepository.save(
                GoldenQaPairEntity(
                    datasetId = datasetId,
                    qaCode = qaCode,
                    category = category.name,
                    questionType = questionType.name,
                    difficulty = difficulty.name,
                    riskLevel = riskLevel.name,
                    question = generated.question,
                    expectedAnswer = generated.expected_answer,
                    mustInclude = generated.must_include,
                    mustNotInclude = generated.must_not_include,
                    sourceMustMatch = true,
                ),
            )

        // 출처 저장
        qaSourceRepository.save(
            GoldenQaSourceEntity(
                qaPairId = qaPair.id!!,
                policyArticleId = article.articleId,
                insuranceCompany = article.companyName,
                productName = article.productName,
                articleNumber = article.articleNumber,
                contentType = category.name,
            ),
        )
    }

    /** 카테고리별 약관 조문 조회 (보험사/상품 정보 포함) */
    private suspend fun findArticlesByContentType(contentType: ContentType): List<ArticleWithContext> {
        val companies = insuranceCompanyRepository.findAll().toList()
        val result = mutableListOf<ArticleWithContext>()

        for (company in companies) {
            val products = productRepository.findByInsuranceCompanyId(company.id!!).toList()
            for (product in products) {
                val articles =
                    policyArticleRepository
                        .findByProductIdAndContentType(product.id!!, contentType.name)
                        .toList()
                        .filter { it.isActive && it.content.length > 50 }

                for (article in articles) {
                    result.add(
                        ArticleWithContext(
                            articleId = article.id!!,
                            companyName = company.name,
                            productName = product.name,
                            articleNumber = article.articleNumber,
                            content = article.content.take(3000),
                        ),
                    )
                }
            }
        }

        return result.shuffled()
    }

    /** 난이도 결정 */
    private fun determineDifficulty(
        questionType: QuestionType,
        riskLevel: RiskLevel,
    ): Difficulty =
        when {
            questionType == QuestionType.COMPLEX || questionType == QuestionType.COMPARISON -> Difficulty.HARD
            riskLevel == RiskLevel.CRITICAL -> Difficulty.HARD
            questionType == QuestionType.CONDITIONAL -> Difficulty.MEDIUM
            else -> Difficulty.EASY
        }

    data class ArticleWithContext(
        val articleId: UUID,
        val companyName: String,
        val productName: String,
        val articleNumber: String?,
        val content: String,
    )
}
