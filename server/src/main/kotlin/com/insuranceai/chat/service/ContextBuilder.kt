package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductFeatureRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.ProductYieldScenarioRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import com.insuranceai.search.dto.FusedSearchResult
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.util.UUID

data class ContextResult(
    val contextText: String,
    val sources: List<SourceInfo>,
    val tokenEstimate: Int,
    val searchQuality: SearchQuality = SearchQuality.HIGH,
)

/** 동적 토큰 예산 배분 결과 */
data class Budgets(
    val article: Int,
    val supplementary: Int,
    val history: Int,
)

/** 검색 결과 품질 등급 */
enum class SearchQuality {
    NO_RESULTS,
    LOW,
    MEDIUM,
    HIGH,
}

@Service
class ContextBuilder(
    private val surrenderValueRepository: SurrenderValueRepository,
    private val premiumExampleRepository: PremiumExampleRepository,
    private val productIndexRepository: ProductIndexRepository,
    private val productRepository: ProductRepository,
    private val productFeatureRepository: ProductFeatureRepository,
    private val productYieldScenarioRepository: ProductYieldScenarioRepository,
    private val productMetricRepository: ProductMetricRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    companion object {
        const val MAX_ARTICLES = 20
        const val MAX_MESSAGES = 20
        const val TOKEN_BUDGET = 40_000
        const val ARTICLE_BUDGET = 20_000
        const val SUPPLEMENTARY_BUDGET = 5_000
        const val HISTORY_BUDGET = 8_000
    }

    /** 동적 예산 배분: 대화 이력과 보충 데이터 사용량에 따라 약관 자료 예산을 확대 */
    internal fun calculateBudgets(
        historyTokens: Int,
        hasSupplementary: Boolean,
    ): Budgets {
        val usedByHistory = minOf(historyTokens, HISTORY_BUDGET)
        val usedBySupp = if (hasSupplementary) SUPPLEMENTARY_BUDGET else 0
        // 남는 예산을 약관 자료에 재배분
        val articleBudget = TOKEN_BUDGET - usedByHistory - usedBySupp
        return Budgets(
            article = minOf(articleBudget, ARTICLE_BUDGET),
            supplementary = usedBySupp,
            history = usedByHistory,
        )
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

        // 동적 예산 계산
        val historySection = buildHistorySection(conversationHistory)
        val historyTokens = estimateTokens(historySection)
        val productIds = searchResults.take(MAX_ARTICLES).map { it.productId }.distinct()
        val supplementarySection = buildSupplementarySection(productIds, analysis)
        val hasSupplementary = supplementarySection.isNotBlank()
        val budgets = calculateBudgets(historyTokens, hasSupplementary)

        // 1. 검색된 약관 자료
        val articlesSection = buildArticlesSection(searchResults.take(MAX_ARTICLES), sources)
        val articlesTokens = estimateTokens(articlesSection)
        if (articlesTokens <= budgets.article) {
            contextParts.add(articlesSection)
            totalTokens += articlesTokens
        } else {
            // 토큰 예산 초과 시 낮은 유사도 조문부터 제거
            val truncated = truncateArticles(searchResults, sources, budgets.article)
            contextParts.add(truncated)
            totalTokens += estimateTokens(truncated)
        }

        // 2. 보충 정형 데이터
        val suppTokens = estimateTokens(supplementarySection)
        if (hasSupplementary && totalTokens + suppTokens <= TOKEN_BUDGET) {
            if (suppTokens <= budgets.supplementary) {
                contextParts.add(supplementarySection)
                totalTokens += suppTokens
            } else {
                val truncated = supplementarySection.take((budgets.supplementary / 1.5).toInt())
                contextParts.add(truncated)
                totalTokens += estimateTokens(truncated)
            }
        }

        // 3. 대화 이력
        if (historySection.isNotBlank() && totalTokens + historyTokens <= TOKEN_BUDGET) {
            if (historyTokens <= budgets.history) {
                contextParts.add(historySection)
                totalTokens += historyTokens
            } else {
                // 오래된 메시지부터 제거
                val truncated = truncateHistory(conversationHistory, budgets.history)
                contextParts.add(truncated)
                totalTokens += estimateTokens(truncated)
            }
        }

        return ContextResult(
            contextText = contextParts.joinToString("\n\n"),
            sources = sources,
            tokenEstimate = totalTokens,
            searchQuality = assessSearchQuality(searchResults),
        )
    }

    /** 검색 결과 품질 평가 */
    internal fun assessSearchQuality(results: List<FusedSearchResult>): SearchQuality {
        if (results.isEmpty()) return SearchQuality.NO_RESULTS
        val avgScore = results.take(5).map { it.relevanceScore.toDouble() }.average()
        return when {
            avgScore < 0.3 -> SearchQuality.LOW
            avgScore < 0.6 -> SearchQuality.MEDIUM
            else -> SearchQuality.HIGH
        }
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

            sb.appendLine("### [$sourceNum] ${result.companyName} - ${result.productName} - $articleRef")
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
                    contentType = result.contentType,
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
        val sb = StringBuilder("## 보충 정형 데이터\n\n")

        // 상품 기본 정보 (항상 포함)
        for (productId in productIds) {
            val product = productRepository.findById(productId) ?: continue
            val company = insuranceCompanyRepository.findById(product.insuranceCompanyId)

            sb.appendLine("### 상품 기본 정보: ${company?.name ?: ""} ${product.name}")
            sb.appendLine("- 카테고리: ${product.category}")
            product.subcategory?.let { sb.appendLine("- 소분류: $it") }
            product.renewalType?.let { sb.appendLine("- 갱신유형: $it") }
            product.salesChannel?.let { sb.appendLine("- 판매채널: $it") }
            product.surrenderType?.let { sb.appendLine("- 해지유형: $it") }
            product.productType?.let { sb.appendLine("- 상품유형: $it") }
            if (product.isUniversal) sb.appendLine("- 유니버설: 예")
            product.expenseRatio?.let { sb.appendLine("- 사업비율: $it%") }
            product.notes?.let { sb.appendLine("- 특이사항: $it") }
            sb.appendLine()
        }

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
                        val amountDisplay = e.insuranceAmountText ?: e.insuranceAmount?.toString() ?: "-"
                        sb.appendLine(
                            "| ${e.gender} | ${e.age} | ${e.insurancePeriod} | ${e.paymentPeriod} | $amountDisplay | ${e.premium} |",
                        )
                    }
                    sb.appendLine()
                }

                // 이율 정보 표시
                val product = productRepository.findById(productId)
                if (product != null) {
                    val hasRateInfo =
                        product.declaredInterestRate != null ||
                            product.guaranteedInterestRate != null ||
                            product.guaranteedInterestRateText != null ||
                            product.appliedInterestRate != null
                    if (hasRateInfo) {
                        sb.appendLine("### 이율 정보")
                        product.appliedInterestRate?.let { sb.appendLine("- 적용이율: $it%") }
                        product.declaredInterestRate?.let { sb.appendLine("- 공시이율: $it%") }
                        if (product.guaranteedInterestRateText != null) {
                            sb.appendLine("- 최저보증이율: ${product.guaranteedInterestRateText}")
                        } else if (product.guaranteedInterestRate != null) {
                            sb.appendLine("- 최저보증이율: ${product.guaranteedInterestRate}%")
                        }
                        sb.appendLine()
                    }
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

        if (needs.productFeature) {
            for (productId in productIds) {
                val features = productFeatureRepository.findByProductId(productId).toList()
                if (features.isNotEmpty()) {
                    sb.appendLine("### 상품 특징")
                    features.forEach { f ->
                        sb.appendLine("- ${f.key}: ${f.value}")
                    }
                    sb.appendLine()
                }
            }
        }

        if (needs.yieldScenario) {
            for (productId in productIds) {
                val scenarios = productYieldScenarioRepository.findByProductId(productId).toList()
                if (scenarios.isNotEmpty()) {
                    sb.appendLine("### 수익률 시나리오")
                    sb.appendLine("| 시나리오 | 수익률 | 성별 |")
                    sb.appendLine("|---------|-------|------|")
                    scenarios.forEach { s ->
                        sb.appendLine("| ${s.scenarioLabel} | ${s.yieldRate}% | ${s.gender ?: "-"} |")
                    }
                    sb.appendLine()
                }
            }
        }

        if (needs.productMetric) {
            for (productId in productIds) {
                val metrics = productMetricRepository.findByProductId(productId).toList()
                if (metrics.isNotEmpty()) {
                    sb.appendLine("### 상품 지표")
                    sb.appendLine("| 지표유형 | 값 | 기준일 |")
                    sb.appendLine("|---------|---|-------|")
                    metrics.forEach { m ->
                        sb.appendLine("| ${m.metricType} | ${m.metricValue} | ${m.referenceDate} |")
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
