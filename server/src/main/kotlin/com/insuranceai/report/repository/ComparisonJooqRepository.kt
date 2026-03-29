package com.insuranceai.report.repository

import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.ProductCategories
import com.insuranceai.report.dto.CompanyInfo
import com.insuranceai.report.dto.ComparisonCategoryInfo
import com.insuranceai.report.dto.CoverageItem
import com.insuranceai.report.dto.ExemptionItem
import com.insuranceai.report.dto.PremiumComparisonItem
import com.insuranceai.report.dto.ProductInfo
import com.insuranceai.report.dto.SurrenderComparisonItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.UUID

@Repository
class ComparisonJooqRepository(
    private val dsl: DSLContext,
) {
    // --- 테이블 ---
    private val insuranceCompanies = DSL.table("insurance_companies")
    private val products = DSL.table("products")
    private val policyArticles = DSL.table("policy_articles")
    private val premiumExamples = DSL.table("premium_examples")
    private val surrenderValues = DSL.table("surrender_values")

    // --- insurance_companies 필드 ---
    private val icId = DSL.field("insurance_companies.id", UUID::class.java)
    private val icName = DSL.field("insurance_companies.name", String::class.java)
    private val icType = DSL.field("insurance_companies.type", String::class.java)

    // --- products 필드 ---
    private val pId = DSL.field("products.id", UUID::class.java)
    private val pName = DSL.field("products.name", String::class.java)
    private val pCategory = DSL.field("products.category", String::class.java)
    private val pSubcategory = DSL.field("products.subcategory", String::class.java)
    private val pRenewalType = DSL.field("products.renewal_type", String::class.java)
    private val pInsuranceCompanyId = DSL.field("products.insurance_company_id", UUID::class.java)
    private val pIsActive = DSL.field("products.is_active", Boolean::class.java)

    // --- policy_articles 필드 ---
    private val paId = DSL.field("policy_articles.id", UUID::class.java)
    private val paProductId = DSL.field("policy_articles.product_id", UUID::class.java)
    private val paArticleNumber = DSL.field("policy_articles.article_number", String::class.java)
    private val paArticleTitle = DSL.field("policy_articles.article_title", String::class.java)
    private val paContent = DSL.field("policy_articles.content", String::class.java)
    private val paContentType = DSL.field("policy_articles.content_type", String::class.java)
    private val paIsActive = DSL.field("policy_articles.is_active", Boolean::class.java)

    // --- premium_examples 필드 ---
    private val peProductId = DSL.field("premium_examples.product_id", UUID::class.java)
    private val peGender = DSL.field("premium_examples.gender", String::class.java)
    private val peAge = DSL.field("premium_examples.age", Int::class.java)
    private val peInsurancePeriod = DSL.field("premium_examples.insurance_period", String::class.java)
    private val pePaymentPeriod = DSL.field("premium_examples.payment_period", String::class.java)
    private val peInsuranceAmount = DSL.field("premium_examples.insurance_amount", Long::class.java)
    private val pePremium = DSL.field("premium_examples.premium", Long::class.java)

    // --- surrender_values 필드 ---
    private val svProductId = DSL.field("surrender_values.product_id", UUID::class.java)
    private val svElapsedMonths = DSL.field("surrender_values.elapsed_months", Int::class.java)
    private val svPremiumPaid = DSL.field("surrender_values.premium_paid", Long::class.java)
    private val svSurrenderValue = DSL.field("surrender_values.surrender_value", Long::class.java)
    private val svSurrenderRate = DSL.field("surrender_values.surrender_rate", Double::class.java)

    /** 보험사별 해당 카테고리 상품 조회 */
    suspend fun queryProductsByCompany(
        companyIds: List<UUID>,
        category: String,
        subcategory: String?,
    ): Map<CompanyInfo, List<ProductInfo>> =
        withContext(Dispatchers.IO) {
            var step =
                dsl
                    .select(icId, icName, icType, pId, pName, pRenewalType)
                    .from(insuranceCompanies)
                    .join(products)
                    .on(pInsuranceCompanyId.eq(icId))
                    .where(icId.`in`(companyIds))
                    .and(pIsActive.eq(true))

            // category는 subcategory 컬럼에 매칭 (DB의 category는 "보장성" 등 대분류)
            step = step.and(pSubcategory.eq(category))

            val records =
                Flux
                    .from(step.orderBy(icName, pName))
                    .collectList()
                    .awaitSingle()

            records.groupBy(
                { record ->
                    CompanyInfo(
                        id = record.get(icId)!!,
                        name = record.get(icName)!!,
                        type = record.get(icType)!!,
                    )
                },
                { record ->
                    ProductInfo(
                        id = record.get(pId)!!,
                        name = record.get(pName)!!,
                        renewalType = record.get(pRenewalType),
                        insurancePeriod = null,
                    )
                },
            )
        }

    /** 상품별 보장내용 약관 조항 조회 */
    suspend fun queryCoveragesByProducts(productIds: List<UUID>): Map<UUID, List<CoverageItem>> =
        withContext(Dispatchers.IO) {
            if (productIds.isEmpty()) return@withContext emptyMap()

            val records =
                Flux
                    .from(
                        dsl
                            .select(paId, paProductId, paArticleNumber, paArticleTitle, paContent)
                            .from(policyArticles)
                            .where(paProductId.`in`(productIds))
                            .and(paContentType.eq(ContentType.COVERAGE.name))
                            .and(paIsActive.eq(true))
                            .orderBy(paProductId, paArticleNumber),
                    ).collectList()
                    .awaitSingle()

            records.groupBy(
                { it.get(paProductId)!! },
                { record ->
                    val content = record.get(paContent) ?: ""
                    CoverageItem(
                        name = record.get(paArticleTitle) ?: "미확인",
                        amount = extractAmount(content),
                        conditions = extractConditions(content),
                        articleRef = record.get(paArticleNumber)?.let { "제${it}조" },
                        articleId = record.get(paId),
                    )
                },
            )
        }

    /** 상품별 면책/감액 약관 조항 조회 */
    suspend fun queryExemptionsByProducts(productIds: List<UUID>): Map<UUID, List<ExemptionItem>> =
        withContext(Dispatchers.IO) {
            if (productIds.isEmpty()) return@withContext emptyMap()

            val exemptionTypes = listOf(ContentType.EXEMPTION.name, ContentType.REDUCTION.name)

            val records =
                Flux
                    .from(
                        dsl
                            .select(paProductId, paArticleNumber, paArticleTitle, paContent)
                            .from(policyArticles)
                            .where(paProductId.`in`(productIds))
                            .and(paContentType.`in`(exemptionTypes))
                            .and(paIsActive.eq(true))
                            .orderBy(paProductId, paArticleNumber),
                    ).collectList()
                    .awaitSingle()

            records.groupBy(
                { it.get(paProductId)!! },
                { record ->
                    val content = record.get(paContent) ?: ""
                    ExemptionItem(
                        name = record.get(paArticleTitle) ?: "미확인",
                        description = content.take(200),
                        period = extractExemptionPeriod(content),
                        articleRef = record.get(paArticleNumber)?.let { "제${it}조" },
                    )
                },
            )
        }

    /** 보험료 예시 조회 (age/gender 매칭) */
    suspend fun queryPremiumExamples(
        productIds: List<UUID>,
        age: Int?,
        gender: String?,
    ): Map<UUID, PremiumComparisonItem?> =
        withContext(Dispatchers.IO) {
            if (productIds.isEmpty()) return@withContext emptyMap()

            // gender 변환: MALE→남, FEMALE→여
            val dbGender =
                when (gender) {
                    "MALE" -> "남"
                    "FEMALE" -> "여"
                    else -> null
                }

            var step =
                dsl
                    .select(peProductId, peAge, peGender, peInsurancePeriod, pePaymentPeriod, peInsuranceAmount, pePremium)
                    .from(premiumExamples)
                    .where(peProductId.`in`(productIds))

            if (dbGender != null) {
                step = step.and(peGender.eq(dbGender))
            }

            val records =
                Flux
                    .from(step.orderBy(peProductId, peAge))
                    .collectList()
                    .awaitSingle()

            // 상품별로 그룹핑 후 가장 적합한 보험료 예시 선택
            val grouped = records.groupBy { it.get(peProductId)!! }

            productIds.associateWith { productId ->
                val items = grouped[productId] ?: return@associateWith null
                // 정확한 나이 매칭 → 가장 가까운 나이 → 첫 번째
                val bestMatch =
                    if (age != null) {
                        items.sortedBy { kotlin.math.abs((it.get(peAge) ?: 0) - age) }.firstOrNull()
                    } else {
                        items.firstOrNull()
                    }
                bestMatch?.let { record ->
                    PremiumComparisonItem(
                        age = record.get(peAge),
                        gender = record.get(peGender),
                        insurancePeriod = record.get(peInsurancePeriod),
                        paymentPeriod = record.get(pePaymentPeriod),
                        monthlyPremium = record.get(pePremium),
                        insuranceAmountText = record.get(peInsuranceAmount)?.let { formatAmount(it) },
                    )
                }
            }
        }

    /** 대표 시점 해약환급금 조회 (60개월 또는 120개월) */
    suspend fun querySurrenderSamples(productIds: List<UUID>): Map<UUID, SurrenderComparisonItem?> =
        withContext(Dispatchers.IO) {
            if (productIds.isEmpty()) return@withContext emptyMap()

            val targetMonths = listOf(60, 120)

            val records =
                Flux
                    .from(
                        dsl
                            .select(svProductId, svElapsedMonths, svPremiumPaid, svSurrenderValue, svSurrenderRate)
                            .from(surrenderValues)
                            .where(svProductId.`in`(productIds))
                            .and(svElapsedMonths.`in`(targetMonths))
                            .orderBy(svProductId, svElapsedMonths),
                    ).collectList()
                    .awaitSingle()

            val grouped = records.groupBy { it.get(svProductId)!! }

            productIds.associateWith { productId ->
                val items = grouped[productId] ?: return@associateWith null
                // 120개월 우선, 없으면 60개월
                val best = items.firstOrNull { it.get(svElapsedMonths) == 120 } ?: items.firstOrNull()
                best?.let { record ->
                    SurrenderComparisonItem(
                        elapsedMonths = record.get(svElapsedMonths),
                        premiumPaid = record.get(svPremiumPaid),
                        surrenderValue = record.get(svSurrenderValue),
                        surrenderRate = record.get(svSurrenderRate),
                    )
                }
            }
        }

    /** 비교 가능한 카테고리 목록 조회 (2개 이상 보험사에 상품이 있는 카테고리) */
    suspend fun queryAvailableCategories(): List<ComparisonCategoryInfo> =
        withContext(Dispatchers.IO) {
            val subcategoryField = DSL.field("products.subcategory", String::class.java)
            val companyCount = DSL.countDistinct(pInsuranceCompanyId)
            val productCount = DSL.count()
            // 카테고리별 보험사 타입 (동일 카테고리 내 보험사 타입은 항상 동일)
            val insuranceTypeField = DSL.min(icType).`as`("insurance_type")

            val records =
                Flux
                    .from(
                        dsl
                            .select(subcategoryField, companyCount, productCount, insuranceTypeField)
                            .from(products)
                            .join(insuranceCompanies)
                            .on(pInsuranceCompanyId.eq(icId))
                            .where(pIsActive.eq(true))
                            .and(subcategoryField.isNotNull)
                            .groupBy(subcategoryField)
                            .having(companyCount.ge(2))
                            .orderBy(productCount.desc()),
                    ).collectList()
                    .awaitSingle()

            val insuranceTypeAlias = DSL.field("insurance_type", String::class.java)

            records.map { record ->
                val code = record.get(subcategoryField)!!
                ComparisonCategoryInfo(
                    code = code,
                    name = resolveSubcategoryName(code),
                    productCount = record.get(productCount) ?: 0,
                    insuranceType = record.get(insuranceTypeAlias) ?: "LIFE",
                )
            }
        }

    // --- 유틸리티 ---

    companion object {
        /** 전체 subcategory 코드 → 한글 매핑 (생보 ProductCategories + 손보 크롤러 코드) */
        private val SUBCATEGORY_NAME_MAP: Map<String, String> by lazy {
            val map = mutableMapOf<String, String>()
            // 생보: ProductCategories 대분류
            ProductCategories.ALL.forEach { cat -> map[cat.code] = cat.name }
            // 생보: ProductCategories subcategory
            ProductCategories.ALL.flatMap { it.subcategories }.forEach { sub -> map[sub.code] = sub.name }
            // 손보: 장기보장성
            map["PB11"] = "화재"
            map["PB12"] = "종합"
            map["PB13"] = "운전자"
            map["PB14"] = "상해"
            map["PB15"] = "질병"
            map["PB16"] = "간병/치매"
            map["PB17"] = "기타"
            map["PB18"] = "비용"
            map["PB22"] = "암"
            map["PB24"] = "어린이"
            map["PB25"] = "치아"
            map["PB27"] = "반려동물"
            // 손보: 실손의료
            map["PB21"] = "노후실손의료"
            map["PB23"] = "유병력자실손"
            map["PB26"] = "실손의료(4세대)"
            // 손보: 장기저축성
            map["PC12"] = "장기저축성"
            // 손보: 연금저축
            map["PC99"] = "연금저축"
            // 생보 추가
            map["PENSION_SAVINGS"] = "연금저축"
            map
        }
    }

    /** subcategory 코드 → 한글 이름 변환 */
    private fun resolveSubcategoryName(code: String): String = SUBCATEGORY_NAME_MAP[code] ?: code

    /** 보장 금액 추출 (예: "3,000만원", "1억원") */
    private fun extractAmount(content: String): String? {
        val amountPattern = Regex("(\\d[\\d,]*\\s*(만원|억원|원))")
        return amountPattern.find(content)?.value
    }

    /** 보장 조건 추출 (첫 문장 또는 200자 이내) */
    private fun extractConditions(content: String): String? {
        if (content.isBlank()) return null
        val firstSentence = content.split(Regex("[.。]")).firstOrNull()?.trim()
        return firstSentence?.take(200)
    }

    /** 면책기간 추출 (예: "90일", "1년") */
    private fun extractExemptionPeriod(content: String): String? {
        val periodPattern = Regex("(\\d+\\s*(일|개월|년|년간))")
        return periodPattern.find(content)?.value
    }

    /** 금액 포맷 (예: 10000000 → "1,000만원") */
    private fun formatAmount(amount: Long): String {
        if (amount >= 100_000_000) {
            val eok = amount / 100_000_000
            val remainder = amount % 100_000_000
            return if (remainder > 0) {
                "${eok}억 ${String.format("%,d", remainder / 10_000)}만원"
            } else {
                "${eok}억원"
            }
        }
        if (amount >= 10_000) {
            return "${String.format("%,d", amount / 10_000)}만원"
        }
        return "${String.format("%,d", amount)}원"
    }
}
