package com.insuranceai.evaluation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.config.EvalConfig
import com.insuranceai.evaluation.domain.Difficulty
import com.insuranceai.evaluation.domain.PracticeCategory
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
    val practice_scenario: String? = null,
    val ground_truth_sources: List<GeneratedSource> = emptyList(),
)

data class GeneratedSource(
    val article_number: String? = null,
    val article_title: String? = null,
    val content_type: String? = null,
)

/** 거절 QA LLM 응답 파싱용 DTO */
data class GeneratedRejectionQaPair(
    val question: String,
    val expected_answer: String,
    val rejection_reason: String? = null,
    val must_include: List<String> = emptyList(),
    val must_not_include: List<String> = emptyList(),
)

@Service
class SilverGeneratorService(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
    private val objectMapper: ObjectMapper,
    private val evalConfig: EvalConfig,
    private val qaPairRepository: GoldenQaPairRepository,
    private val qaSourceRepository: GoldenQaSourceRepository,
    private val policyArticleRepository: PolicyArticleRepository,
    private val productRepository: ProductRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ─── v1: 조문 중심 생성 계획 (기존 호환) ───

    /** 배치 분배 계획 */
    data class GenerationPlan(
        val category: ContentType,
        val count: Int,
        val questionTypes: List<QuestionType>,
        val riskLevels: List<RiskLevel>,
    )

    /** v1 카테고리별 생성 계획 */
    private val generationPlansV1 =
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

    // ─── v2: 설계사 실무 시나리오 중심 생성 계획 ───

    /** 실무 카테고리별 생성 계획 */
    data class PracticePlan(
        val category: PracticeCategory,
        val count: Int,
        val questionTypes: List<QuestionType>,
        val riskLevels: List<RiskLevel>,
    )

    /** v2 설계사 실무 카테고리별 생성 계획 (계획서 §1.1, §1.2 기반) */
    private val generationPlansV2 =
        listOf(
            PracticePlan(
                PracticeCategory.COVERAGE_CHECK,
                25,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.CONDITIONAL),
                listOf(RiskLevel.CRITICAL, RiskLevel.HIGH),
            ),
            PracticePlan(
                PracticeCategory.EXCLUSION_EXPLAIN,
                20,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.CONDITIONAL),
                listOf(RiskLevel.CRITICAL, RiskLevel.HIGH),
            ),
            PracticePlan(
                PracticeCategory.CLAIM_PROCESS,
                15,
                listOf(QuestionType.SINGLE_LOOKUP),
                listOf(RiskLevel.MEDIUM, RiskLevel.LOW),
            ),
            PracticePlan(
                PracticeCategory.PRODUCT_COMPARE,
                10,
                listOf(QuestionType.COMPARISON),
                listOf(RiskLevel.HIGH, RiskLevel.MEDIUM),
            ),
            PracticePlan(
                PracticeCategory.SPECIAL_TERMS,
                10,
                listOf(QuestionType.SINGLE_LOOKUP, QuestionType.COMPLEX),
                listOf(RiskLevel.HIGH, RiskLevel.MEDIUM),
            ),
            PracticePlan(
                PracticeCategory.CONDITION_CHECK,
                10,
                listOf(QuestionType.CONDITIONAL, QuestionType.COMPLEX),
                listOf(RiskLevel.HIGH, RiskLevel.MEDIUM),
            ),
        )

    // ─── 거절 카테고리 (v2 LLM 생성용) ───

    private val rejectionCategories =
        listOf(
            "가입 추천/판단",
            "상품 추천",
            "수수료/수당",
            "승환 유도",
            "보장 충분성 판단",
            "미보유 상품",
            "일반 상식",
            "무관한 질문",
            "투자 조언",
            "의료 판단",
        )

    /** Silver 데이터셋 생성 (프롬프트 버전에 따라 분기) */
    suspend fun generateSilverDataset(
        datasetId: UUID,
        targetCount: Int = 90,
    ): Int =
        when (evalConfig.promptVersion) {
            "v2" -> generateSilverDatasetV2(datasetId, targetCount)
            else -> generateSilverDatasetV1(datasetId, targetCount)
        }

    /** 거절 대상 QA 생성 (프롬프트 버전에 따라 분기) */
    suspend fun generateRejectionQaPairs(datasetId: UUID): Int =
        when (evalConfig.promptVersion) {
            "v2" -> generateRejectionQaPairsV2(datasetId)
            else -> generateRejectionQaPairsV1(datasetId)
        }

    // ═══════════════════════════════════════════════
    // v2: 설계사 실무 시나리오 중심 생성
    // ═══════════════════════════════════════════════

    /** 비례 배분 계산 (소수점 이하 누적 보정) */
    private fun distributeProportionally(
        plans: List<Int>,
        targetCount: Int,
        totalPlanCount: Int,
    ): List<Int> {
        if (targetCount >= totalPlanCount) {
            return plans.map { (it * targetCount) / totalPlanCount }
        }
        // 소수점 이하 누적 보정으로 최소 1개 보장
        val result = mutableListOf<Int>()
        var remaining = targetCount
        for ((i, planCount) in plans.withIndex()) {
            if (remaining <= 0) {
                result.add(0)
                continue
            }
            val ratio = planCount.toDouble() / totalPlanCount
            val ideal = ratio * targetCount
            val count =
                if (i == plans.lastIndex) {
                    remaining
                } else {
                    ideal.toInt().coerceAtLeast(if (remaining > 0 && ideal > 0.3) 1 else 0)
                }
            result.add(count.coerceAtMost(remaining))
            remaining -= result.last()
        }
        return result
    }

    /** v2 Silver 데이터셋 생성 (90개 LLM 생성 - 실무 시나리오 중심) */
    private suspend fun generateSilverDatasetV2(
        datasetId: UUID,
        targetCount: Int,
    ): Int {
        var totalGenerated = 0
        var qaCodeCounter = 1

        val adjustedCounts =
            distributeProportionally(
                plans = generationPlansV2.map { it.count },
                targetCount = targetCount,
                totalPlanCount = 90,
            )

        for ((index, plan) in generationPlansV2.withIndex()) {
            val adjustedCount = adjustedCounts[index]

            log.info("[v2] 카테고리 {} 생성 시작 ({}개)", plan.category, adjustedCount)

            if (plan.category == PracticeCategory.PRODUCT_COMPARE) {
                // 비교 질문: 2개 상품 조문 동시 제공
                val generated =
                    generateComparisonQaPairs(
                        datasetId = datasetId,
                        plan = plan,
                        targetCount = adjustedCount,
                        qaCodeStart = qaCodeCounter,
                    )
                qaCodeCounter += generated
                totalGenerated += generated
                continue
            }

            // 관련 조문 묶음 조회
            val articleGroups = gatherRelatedArticleGroups(plan.category)
            if (articleGroups.isEmpty()) {
                log.warn("[v2] 카테고리 {}에 해당하는 약관 데이터가 없습니다", plan.category)
                continue
            }

            var generated = 0
            var groupIndex = 0

            while (generated < adjustedCount && groupIndex < articleGroups.size * 3) {
                val group = articleGroups[groupIndex % articleGroups.size]
                val questionType = plan.questionTypes[generated % plan.questionTypes.size]
                val riskLevel = plan.riskLevels[generated % plan.riskLevels.size]
                val difficulty = determineDifficulty(questionType, riskLevel)

                try {
                    val qaCode = "GD-%03d".format(qaCodeCounter)

                    val result =
                        generateSingleQaPairV2(
                            articleGroup = group,
                            practiceCategory = plan.category,
                            questionType = questionType,
                            difficulty = difficulty,
                        )

                    if (result != null) {
                        saveQaPairV2(
                            datasetId = datasetId,
                            qaCode = qaCode,
                            articleGroup = group,
                            practiceCategory = plan.category,
                            questionType = questionType,
                            difficulty = difficulty,
                            riskLevel = riskLevel,
                            generated = result,
                        )
                        generated++
                        qaCodeCounter++
                        totalGenerated++
                        log.debug("[v2] 생성 완료: {} (카테고리: {})", qaCode, plan.category)
                    }
                } catch (e: Exception) {
                    log.warn("[v2] QA 생성 실패 (카테고리: {}, 시도: {}): {}", plan.category, groupIndex, e.message)
                }

                groupIndex++
                delay(500)
            }

            log.info("[v2] 카테고리 {} 생성 완료: {}개", plan.category, generated)
        }

        log.info("[v2] Silver 데이터셋 생성 완료: 총 {}개", totalGenerated)
        return totalGenerated
    }

    /** v2 비교 질문 생성 (2개 상품 조문 동시 제공) */
    private suspend fun generateComparisonQaPairs(
        datasetId: UUID,
        plan: PracticePlan,
        targetCount: Int,
        qaCodeStart: Int,
    ): Int {
        val productPairs = gatherProductPairsForComparison(plan.category)
        if (productPairs.isEmpty()) {
            log.warn("[v2] 비교 질문 생성을 위한 상품 쌍이 없습니다")
            return 0
        }

        var generated = 0
        var pairIndex = 0
        var qaCodeCounter = qaCodeStart

        while (generated < targetCount && pairIndex < productPairs.size * 3) {
            val (group1, group2) = productPairs[pairIndex % productPairs.size]
            val questionType = plan.questionTypes[generated % plan.questionTypes.size]
            val riskLevel = plan.riskLevels[generated % plan.riskLevels.size]
            val difficulty = Difficulty.HARD

            try {
                val qaCode = "GD-%03d".format(qaCodeCounter)

                // 2개 상품 조문을 합쳐서 프롬프트에 제공
                val comparisonContent =
                    """
                    ## 상품 A: ${group1.productName} (${group1.companyName})
                    ${group1.combinedContent}

                    ## 상품 B: ${group2.productName} (${group2.companyName})
                    ${group2.combinedContent}
                    """.trimIndent()

                val comparisonGroup =
                    ArticleGroup(
                        companyName = "${group1.companyName} vs ${group2.companyName}",
                        productName = "${group1.productName} vs ${group2.productName}",
                        productId = group1.productId,
                        combinedContent = comparisonContent,
                        articles = group1.articles + group2.articles,
                    )

                val result =
                    generateSingleQaPairV2(
                        articleGroup = comparisonGroup,
                        practiceCategory = PracticeCategory.PRODUCT_COMPARE,
                        questionType = questionType,
                        difficulty = difficulty,
                    )

                if (result != null) {
                    saveQaPairV2(
                        datasetId = datasetId,
                        qaCode = qaCode,
                        articleGroup = comparisonGroup,
                        practiceCategory = PracticeCategory.PRODUCT_COMPARE,
                        questionType = questionType,
                        difficulty = difficulty,
                        riskLevel = riskLevel,
                        generated = result,
                    )
                    generated++
                    qaCodeCounter++
                    log.debug("[v2] 비교 QA 생성 완료: {}", qaCode)
                }
            } catch (e: Exception) {
                log.warn("[v2] 비교 QA 생성 실패 (시도: {}): {}", pairIndex, e.message)
            }

            pairIndex++
            delay(500)
        }

        log.info("[v2] 비교 QA 생성 완료: {}개", generated)
        return generated
    }

    /** v2 거절 대상 QA LLM 생성 */
    private suspend fun generateRejectionQaPairsV2(datasetId: UUID): Int {
        val existingCount = qaPairRepository.countByDatasetId(datasetId)
        var qaCodeCounter = existingCount.toInt() + 1

        // 보유 보험사/상품 정보 조회
        val companies = insuranceCompanyRepository.findAll().toList()
        val availableCompanies = companies.joinToString(", ") { it.name }

        val sampleProducts =
            companies
                .take(3)
                .flatMap { company ->
                    productRepository
                        .findByInsuranceCompanyId(company.id!!)
                        .toList()
                        .take(2)
                        .map { "${company.name} ${it.name}" }
                }.joinToString(", ")

        var generated = 0

        for (rejectionCategory in rejectionCategories) {
            try {
                val qaCode = "GD-%03d".format(qaCodeCounter)

                val prompt =
                    promptLoader.render(
                        "evaluation/rejection-generation.st",
                        mapOf(
                            "availableCompanies" to availableCompanies,
                            "sampleProducts" to sampleProducts,
                            "rejectionCategory" to rejectionCategory,
                        ),
                    )

                val response = suspendingChatClient.call(userMessage = prompt)
                val result = parseRejectionResponse(response)

                if (result != null) {
                    qaPairRepository.save(
                        GoldenQaPairEntity(
                            datasetId = datasetId,
                            qaCode = qaCode,
                            category = PracticeCategory.REJECTION.name,
                            questionType = QuestionType.REJECTION.name,
                            difficulty = Difficulty.EASY.name,
                            riskLevel = RiskLevel.HIGH.name,
                            question = result.question,
                            expectedAnswer = result.expected_answer,
                            mustInclude = result.must_include,
                            mustNotInclude = result.must_not_include,
                            sourceMustMatch = false,
                            status = QaPairStatus.GOLD.name,
                        ),
                    )
                    generated++
                    qaCodeCounter++
                    log.debug("[v2] 거절 QA 생성 완료: {} ({})", qaCode, rejectionCategory)
                }

                delay(500)
            } catch (e: Exception) {
                log.warn("[v2] 거절 QA 생성 실패 (카테고리: {}): {}", rejectionCategory, e.message)
            }
        }

        log.info("[v2] 거절 대상 QA {}개 생성 완료", generated)
        return generated
    }

    /** v2 단일 QA 쌍 LLM 생성 */
    private suspend fun generateSingleQaPairV2(
        articleGroup: ArticleGroup,
        practiceCategory: PracticeCategory,
        questionType: QuestionType,
        difficulty: Difficulty,
    ): GeneratedQaPair? {
        val prompt =
            promptLoader.render(
                "evaluation/silver-generation-v2.st",
                mapOf(
                    "insuranceCompany" to articleGroup.companyName,
                    "productName" to articleGroup.productName,
                    "articleContents" to articleGroup.combinedContent,
                    "practiceCategory" to practiceCategory.description,
                    "questionType" to questionType.name,
                    "difficulty" to difficulty.name,
                ),
            )

        val response = suspendingChatClient.call(userMessage = prompt)
        return parseQaPairResponse(response)
    }

    /** v2 QA 쌍 저장 */
    private suspend fun saveQaPairV2(
        datasetId: UUID,
        qaCode: String,
        articleGroup: ArticleGroup,
        practiceCategory: PracticeCategory,
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
                    category = practiceCategory.name,
                    questionType = questionType.name,
                    difficulty = difficulty.name,
                    riskLevel = riskLevel.name,
                    question = generated.question,
                    expectedAnswer = generated.expected_answer,
                    mustInclude = generated.must_include,
                    mustNotInclude = generated.must_not_include,
                    sourceMustMatch = practiceCategory != PracticeCategory.REJECTION,
                ),
            )

        // 출처 저장 (관련 조문들의 첫 번째 article을 대표로 저장)
        for (article in articleGroup.articles.take(3)) {
            qaSourceRepository.save(
                GoldenQaSourceEntity(
                    qaPairId = qaPair.id!!,
                    policyArticleId = article.articleId,
                    insuranceCompany = article.companyName,
                    productName = article.productName,
                    articleNumber = article.articleNumber,
                    contentType = article.contentType,
                ),
            )
        }
    }

    /** 카테고리별 관련 조문 묶음 조회 (최대 8000자) */
    private suspend fun gatherRelatedArticleGroups(practiceCategory: PracticeCategory): List<ArticleGroup> {
        val companies = insuranceCompanyRepository.findAll().toList()
        val result = mutableListOf<ArticleGroup>()

        for (company in companies) {
            val products = productRepository.findByInsuranceCompanyId(company.id!!).toList()
            for (product in products) {
                val articles = mutableListOf<ArticleWithContext>()

                // 관련 ContentType들의 조문을 모두 조회
                for (contentType in practiceCategory.relatedContentTypes) {
                    val typeArticles =
                        policyArticleRepository
                            .findByProductIdAndContentType(product.id!!, contentType.name)
                            .toList()
                            .filter { it.isActive && it.content.length > 50 }
                            .map { article ->
                                ArticleWithContext(
                                    articleId = article.id!!,
                                    companyName = company.name,
                                    productName = product.name,
                                    articleNumber = article.articleNumber,
                                    content = article.content,
                                    contentType = contentType.name,
                                )
                            }
                    articles.addAll(typeArticles)
                }

                if (articles.isNotEmpty()) {
                    // 최대 10개 조문, 8000자 제한
                    val selectedArticles = articles.take(10)
                    val combinedContent =
                        selectedArticles
                            .joinToString("\n\n---\n\n") { formatArticle(it) }
                            .take(8000)

                    result.add(
                        ArticleGroup(
                            companyName = company.name,
                            productName = product.name,
                            productId = product.id!!,
                            combinedContent = combinedContent,
                            articles = selectedArticles,
                        ),
                    )
                }
            }
        }

        return result.shuffled()
    }

    /** 비교 질문을 위한 상품 쌍 생성 */
    private suspend fun gatherProductPairsForComparison(practiceCategory: PracticeCategory): List<Pair<ArticleGroup, ArticleGroup>> {
        val groups = gatherRelatedArticleGroups(practiceCategory)
        val pairs = mutableListOf<Pair<ArticleGroup, ArticleGroup>>()

        // 서로 다른 보험사의 상품끼리 쌍을 생성
        for (i in groups.indices) {
            for (j in i + 1 until groups.size) {
                if (groups[i].companyName != groups[j].companyName) {
                    pairs.add(groups[i] to groups[j])
                }
            }
        }

        return pairs.shuffled()
    }

    /** 조문 포맷팅 */
    private fun formatArticle(article: ArticleWithContext): String =
        buildString {
            if (article.articleNumber != null) {
                append("[${article.articleNumber}] ")
            }
            append("(${article.contentType}) ")
            append(article.content)
        }

    // ═══════════════════════════════════════════════
    // v1: 조문 중심 생성 (기존 호환)
    // ═══════════════════════════════════════════════

    /** v1 Silver 데이터셋 생성 (90개 LLM 생성) */
    private suspend fun generateSilverDatasetV1(
        datasetId: UUID,
        targetCount: Int = 90,
    ): Int {
        var totalGenerated = 0
        var qaCodeCounter = 1

        val adjustedCountsV1 =
            distributeProportionally(
                plans = generationPlansV1.map { it.count },
                targetCount = targetCount,
                totalPlanCount = 90,
            )

        for ((index, plan) in generationPlansV1.withIndex()) {
            val adjustedCount = adjustedCountsV1[index]

            log.info("카테고리 {} 생성 시작 ({}개)", plan.category, adjustedCount)

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
                        generateSingleQaPairV1(
                            article = article,
                            category = plan.category,
                            questionType = questionType,
                            difficulty = difficulty,
                            riskLevel = riskLevel,
                        )

                    if (result != null) {
                        saveQaPairV1(
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
                delay(500)
            }

            log.info("카테고리 {} 생성 완료: {}개", plan.category, generated)
        }

        log.info("Silver 데이터셋 생성 완료: 총 {}개", totalGenerated)
        return totalGenerated
    }

    /** v1 거절 대상 QA 10개 수동 생성 */
    private suspend fun generateRejectionQaPairsV1(datasetId: UUID): Int {
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

    /** v1 단일 QA 쌍 LLM 생성 */
    private suspend fun generateSingleQaPairV1(
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

    /** v1 QA 쌍 저장 */
    private suspend fun saveQaPairV1(
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

    // ═══════════════════════════════════════════════
    // 공용 유틸리티
    // ═══════════════════════════════════════════════

    /** LLM 응답 JSON 파싱 */
    private fun parseQaPairResponse(response: String): GeneratedQaPair? =
        try {
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

    /** 거절 QA LLM 응답 파싱 */
    private fun parseRejectionResponse(response: String): GeneratedRejectionQaPair? =
        try {
            val jsonStr =
                if (response.contains("```")) {
                    response
                        .substringAfter("```json", response.substringAfter("```"))
                        .substringBefore("```")
                        .trim()
                } else {
                    response.trim()
                }

            objectMapper.readValue<GeneratedRejectionQaPair>(jsonStr)
        } catch (e: Exception) {
            log.warn("거절 QA 응답 파싱 실패: {}", e.message)
            null
        }

    /** v1용: 카테고리별 약관 조문 조회 (보험사/상품 정보 포함) */
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
                            contentType = contentType.name,
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

    /** 약관 조문 정보 (개별) */
    data class ArticleWithContext(
        val articleId: UUID,
        val companyName: String,
        val productName: String,
        val articleNumber: String?,
        val content: String,
        val contentType: String = "",
    )

    /** 약관 조문 묶음 (상품별 관련 조문 그룹) */
    data class ArticleGroup(
        val companyName: String,
        val productName: String,
        val productId: UUID,
        val combinedContent: String,
        val articles: List<ArticleWithContext>,
    )
}
