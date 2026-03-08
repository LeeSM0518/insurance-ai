package com.insuranceai.policy.dto

import com.insuranceai.search.dto.CompanyInfo
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** 약관 조문 상세 응답 */
data class PolicyArticleResponse(
    val id: UUID,
    val insuranceCompany: CompanyInfo,
    val productId: UUID,
    val productName: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val content: String,
    val contentType: String,
    val sectionType: String?,
    val effectiveDate: LocalDate?,
    val dataSource: String?,
    val dataSourceId: UUID?,
    val hasPdfSource: Boolean,
    val relatedArticles: List<RelatedArticleResponse>,
)

data class RelatedArticleResponse(
    val id: UUID,
    val articleNumber: String?,
    val articleTitle: String?,
    val contentType: String,
)

/** 해약환급금 응답 */
data class SurrenderValueResponse(
    val productId: UUID,
    val values: List<SurrenderValueItem>,
)

data class SurrenderValueItem(
    val elapsedMonths: Int,
    val premiumPaid: Long?,
    val surrenderValue: Long,
    val surrenderRate: BigDecimal?,
)

/** 보험료 예시 응답 */
data class PremiumExampleResponse(
    val productId: UUID,
    val examples: List<PremiumExampleItem>,
)

data class PremiumExampleItem(
    val gender: String,
    val age: Int?,
    val insurancePeriod: String,
    val paymentPeriod: String,
    val insuranceAmount: Long?,
    val insuranceAmountText: String? = null,
    val premium: Long,
)

/** 가격지수 응답 */
data class ProductIndexResponse(
    val productId: UUID,
    val indices: List<ProductIndexItem>,
)

data class ProductIndexItem(
    val indexType: String,
    val gender: String?,
    val coverageName: String?,
    val indexValue: BigDecimal,
    val baseDate: LocalDate,
)

/** 상품 특징 아이템 */
data class ProductFeatureItem(
    val key: String,
    val value: String,
)

/** 수익률 시나리오 아이템 */
data class YieldScenarioItem(
    val scenarioType: String,
    val scenarioLabel: String,
    val yieldRate: BigDecimal,
    val gender: String?,
)

/** 상품 지표 아이템 */
data class ProductMetricItem(
    val metricType: String,
    val metricValue: BigDecimal,
    val referenceDate: LocalDate,
)
