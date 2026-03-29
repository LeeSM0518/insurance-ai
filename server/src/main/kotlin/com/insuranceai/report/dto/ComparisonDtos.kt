package com.insuranceai.report.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

// --- Request ---
data class ComparisonRequest(
    @field:Size(min = 2, max = 5)
    val companyIds: List<UUID>,
    @field:NotBlank
    val category: String,
    val subcategory: String? = null,
    val age: Int? = null,
    val gender: String? = null,
)

// --- Response ---
data class ComparisonResponse(
    val category: String,
    val companies: List<CompanyComparison>,
    val comparisonItems: List<String>,
    val aiSummary: String? = null,
)

data class CompanyComparison(
    val companyId: UUID,
    val companyName: String,
    val insuranceType: String,
    val products: List<ProductComparison>,
)

data class ProductComparison(
    val productId: UUID,
    val productName: String,
    val renewalType: String?,
    val coverages: List<CoverageItem>,
    val exemptions: List<ExemptionItem>,
    val premiumExample: PremiumComparisonItem?,
    val surrenderValueSample: SurrenderComparisonItem?,
    val insurancePeriod: String?,
)

data class CoverageItem(
    val name: String,
    val amount: String?,
    val conditions: String?,
    val articleRef: String?,
    val articleId: UUID?,
)

data class ExemptionItem(
    val name: String,
    val description: String?,
    val period: String?,
    val articleRef: String?,
)

data class PremiumComparisonItem(
    val age: Int?,
    val gender: String?,
    val insurancePeriod: String?,
    val paymentPeriod: String?,
    val monthlyPremium: Long?,
    val insuranceAmountText: String?,
)

data class SurrenderComparisonItem(
    val elapsedMonths: Int?,
    val premiumPaid: Long?,
    val surrenderValue: Long?,
    val surrenderRate: Double?,
)

// --- 내부 조회용 ---
data class CompanyInfo(
    val id: UUID,
    val name: String,
    val type: String,
)

data class ProductInfo(
    val id: UUID,
    val name: String,
    val renewalType: String?,
    val insurancePeriod: String?,
)

data class ComparisonCategoryInfo(
    val code: String,
    val name: String,
    val productCount: Int,
    val insuranceType: String,
)

/** 병렬 조회 결과를 묶는 컨테이너 */
data class CombinedComparisonData(
    val coverages: Map<UUID, List<CoverageItem>>,
    val exemptions: Map<UUID, List<ExemptionItem>>,
    val premiums: Map<UUID, PremiumComparisonItem?>,
    val surrenders: Map<UUID, SurrenderComparisonItem?>,
)
