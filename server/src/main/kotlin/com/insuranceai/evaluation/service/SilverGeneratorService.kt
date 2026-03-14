package com.insuranceai.evaluation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.domain.Difficulty
import com.insuranceai.evaluation.domain.PracticeCategory
import com.insuranceai.evaluation.domain.QaPairStatus
import com.insuranceai.evaluation.domain.QuestionType
import com.insuranceai.evaluation.domain.RiskLevel
import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import com.insuranceai.evaluation.entity.GoldenQaSourceEntity
import com.insuranceai.evaluation.repository.GoldenQaPairRepository
import com.insuranceai.evaluation.repository.GoldenQaSourceRepository
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
    private val qaPairRepository: GoldenQaPairRepository,
    private val qaSourceRepository: GoldenQaSourceRepository,
    private val policyArticleRepository: PolicyArticleRepository,
    private val productRepository: ProductRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실무 카테고리별 생성 계획 */
    data class PracticePlan(
        val category: PracticeCategory,
        val count: Int,
        val questionTypes: List<QuestionType>,
        val riskLevels: List<RiskLevel>,
    )

    /** 설계사 실무 카테고리별 생성 계획 (계획서 §1.1, §1.2 기반) */
    private val generationPlans =
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

    // ─── 공개 API ───

    /** Silver 데이터셋 생성 (재개 가능 - 기존 데이터 확인 후 이어서 생성) */
    suspend fun generateSilverDataset(
        datasetId: UUID,
        targetCount: Int = 90,
    ): Int {
        var totalGenerated = 0
        var qaCodeCounter = getMaxQaCodeNumber(datasetId) + 1

        val adjustedCounts =
            distributeProportionally(
                plans = generationPlans.map { it.count },
                targetCount = targetCount,
                totalPlanCount = 90,
            )

        for ((index, plan) in generationPlans.withIndex()) {
            val adjustedCount = adjustedCounts[index]

            // 이미 생성된 해당 카테고리 QA 수 확인
            val existingCategoryCount =
                qaPairRepository.countByDatasetIdAndCategory(datasetId, plan.category.name).toInt()
            val remainingCount = (adjustedCount - existingCategoryCount).coerceAtLeast(0)

            if (remainingCount == 0) {
                log.info("카테고리 {} 이미 완료 ({}개 존재), 스킵", plan.category, existingCategoryCount)
                continue
            }

            log.info(
                "카테고리 {} 생성 시작 (목표: {}개, 기존: {}개, 추가: {}개)",
                plan.category,
                adjustedCount,
                existingCategoryCount,
                remainingCount,
            )

            if (plan.category == PracticeCategory.PRODUCT_COMPARE) {
                val generated =
                    generateComparisonQaPairs(
                        datasetId = datasetId,
                        plan = plan,
                        targetCount = remainingCount,
                        qaCodeStart = qaCodeCounter,
                    )
                qaCodeCounter += generated
                totalGenerated += generated
                continue
            }

            val articleGroups = gatherRelatedArticleGroups(plan.category)
            if (articleGroups.isEmpty()) {
                log.warn("카테고리 {}에 해당하는 약관 데이터가 없습니다", plan.category)
                continue
            }

            var generated = 0
            var groupIndex = 0

            while (generated < remainingCount && groupIndex < articleGroups.size * 3) {
                val group = articleGroups[groupIndex % articleGroups.size]
                val questionType = plan.questionTypes[generated % plan.questionTypes.size]
                val riskLevel = plan.riskLevels[generated % plan.riskLevels.size]
                val difficulty = determineDifficulty(questionType, riskLevel)

                try {
                    val qaCode = "GD-%03d".format(qaCodeCounter)

                    val result =
                        generateSingleQaPair(
                            articleGroup = group,
                            practiceCategory = plan.category,
                            questionType = questionType,
                            difficulty = difficulty,
                        )

                    if (result != null) {
                        saveQaPair(
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
                        log.debug("생성 완료: {} (카테고리: {})", qaCode, plan.category)
                    }
                } catch (e: Exception) {
                    log.warn("QA 생성 실패 (카테고리: {}, 시도: {}): {}", plan.category, groupIndex, e.message)
                }

                groupIndex++
                delay(500)
            }

            log.info("카테고리 {} 생성 완료: {}개", plan.category, generated)
        }

        log.info("Silver 데이터셋 생성 완료: 총 {}개 (신규)", totalGenerated)
        return totalGenerated
    }

    /** 거절 대상 QA LLM 생성 (재개 가능) */
    suspend fun generateRejectionQaPairs(
        datasetId: UUID,
        targetCount: Int = 10,
    ): Int {
        if (targetCount <= 0) return 0

        // 이미 생성된 거절 QA 확인
        val existingRejectionCount =
            qaPairRepository.countByDatasetIdAndQuestionType(datasetId, QuestionType.REJECTION.name).toInt()
        val maxCount = targetCount.coerceAtMost(rejectionCategories.size)
        if (existingRejectionCount >= maxCount) {
            log.info("거절 QA 이미 완료 ({}개 존재, 목표: {}개), 스킵", existingRejectionCount, maxCount)
            return 0
        }

        var qaCodeCounter = getMaxQaCodeNumber(datasetId) + 1
        val remainingCount = maxCount - existingRejectionCount
        val categoriesToGenerate = rejectionCategories.drop(existingRejectionCount).take(remainingCount)

        log.info("거절 QA 생성 (기존: {}개, 추가: {}개, 목표: {}개)", existingRejectionCount, categoriesToGenerate.size, maxCount)

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

        for (rejectionCategory in categoriesToGenerate) {
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
                    log.debug("거절 QA 생성 완료: {} ({})", qaCode, rejectionCategory)
                }

                delay(500)
            } catch (e: Exception) {
                log.warn("거절 QA 생성 실패 (카테고리: {}): {}", rejectionCategory, e.message)
            }
        }

        log.info("거절 대상 QA {}개 생성 완료", generated)
        return generated
    }

    // ─── 비교 질문 생성 ───

    /** 비교 질문 생성 (2개 상품 조문 동시 제공) */
    private suspend fun generateComparisonQaPairs(
        datasetId: UUID,
        plan: PracticePlan,
        targetCount: Int,
        qaCodeStart: Int,
    ): Int {
        val productPairs = gatherProductPairsForComparison(plan.category)
        if (productPairs.isEmpty()) {
            log.warn("비교 질문 생성을 위한 상품 쌍이 없습니다")
            return 0
        }

        var generated = 0
        var pairIndex = 0
        var qaCodeCounter = qaCodeStart

        while (generated < targetCount && pairIndex < productPairs.size * 3) {
            val (group1, group2) = productPairs[pairIndex % productPairs.size]
            val questionType = plan.questionTypes[generated % plan.questionTypes.size]
            val riskLevel = plan.riskLevels[generated % plan.riskLevels.size]

            try {
                val qaCode = "GD-%03d".format(qaCodeCounter)

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
                    generateSingleQaPair(
                        articleGroup = comparisonGroup,
                        practiceCategory = PracticeCategory.PRODUCT_COMPARE,
                        questionType = questionType,
                        difficulty = Difficulty.HARD,
                    )

                if (result != null) {
                    saveQaPair(
                        datasetId = datasetId,
                        qaCode = qaCode,
                        articleGroup = comparisonGroup,
                        practiceCategory = PracticeCategory.PRODUCT_COMPARE,
                        questionType = questionType,
                        difficulty = Difficulty.HARD,
                        riskLevel = riskLevel,
                        generated = result,
                    )
                    generated++
                    qaCodeCounter++
                    log.debug("비교 QA 생성 완료: {}", qaCode)
                }
            } catch (e: Exception) {
                log.warn("비교 QA 생성 실패 (시도: {}): {}", pairIndex, e.message)
            }

            pairIndex++
            delay(500)
        }

        log.info("비교 QA 생성 완료: {}개", generated)
        return generated
    }

    // ─── LLM 호출 + 저장 ───

    /** 단일 QA 쌍 LLM 생성 */
    private suspend fun generateSingleQaPair(
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

    /** QA 쌍 저장 */
    private suspend fun saveQaPair(
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

        // 출처 저장 (관련 조문 최대 3개)
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

    // ─── 약관 데이터 조회 ───

    /** 카테고리별 관련 조문 묶음 조회 (최대 8000자) */
    private suspend fun gatherRelatedArticleGroups(practiceCategory: PracticeCategory): List<ArticleGroup> {
        val companies = insuranceCompanyRepository.findAll().toList()
        val result = mutableListOf<ArticleGroup>()

        for (company in companies) {
            val products = productRepository.findByInsuranceCompanyId(company.id!!).toList()
            for (product in products) {
                val articles = mutableListOf<ArticleWithContext>()

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

    // ─── 유틸리티 ───

    /** 데이터셋 내 최대 qaCode 번호 조회 */
    private suspend fun getMaxQaCodeNumber(datasetId: UUID): Int {
        val existing = qaPairRepository.findByDatasetId(datasetId).toList()
        return existing
            .mapNotNull { it.qaCode.removePrefix("GD-").toIntOrNull() }
            .maxOrNull() ?: 0
    }

    /** 비례 배분 계산 (소수점 이하 누적 보정) */
    private fun distributeProportionally(
        plans: List<Int>,
        targetCount: Int,
        totalPlanCount: Int,
    ): List<Int> {
        if (targetCount >= totalPlanCount) {
            return plans.map { (it * targetCount) / totalPlanCount }
        }
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

    /** 조문 포맷팅 */
    private fun formatArticle(article: ArticleWithContext): String =
        buildString {
            if (article.articleNumber != null) {
                append("[${article.articleNumber}] ")
            }
            append("(${article.contentType}) ")
            append(article.content)
        }

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

    // ─── 데이터 클래스 ───

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
