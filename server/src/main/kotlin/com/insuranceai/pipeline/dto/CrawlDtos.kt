package com.insuranceai.pipeline.dto

import com.insuranceai.policy.domain.InsuranceType
import java.math.BigDecimal
import java.time.LocalDate

data class CrawlResult(
    val insuranceType: InsuranceType,
    val totalProducts: Int,
    val successCount: Int,
    val failureCount: Int,
    val products: List<ProductRawData>,
    val errors: List<CrawlError>,
)

data class ProductRawData(
    val companyName: String,
    val productName: String,
    val category: String,
    val subcategory: String? = null,
    val renewalType: String? = null,
    val salesChannel: String? = null,
    val coverages: List<CoverageData> = emptyList(),
    val premiumExamples: List<PremiumData> = emptyList(),
    val indices: List<IndexData> = emptyList(),
    val metrics: List<MetricData> = emptyList(),
    val surrenderValues: List<SurrenderData> = emptyList(),
    val pdfSummaryPath: String? = null,
    val sourceUrl: String? = null,
    val checksum: String,
    val remarks: String? = null,
    val subscriptionType: String? = null,
    val copaymentRate: BigDecimal? = null,
    val surrenderType: String? = null,
    val productType: String? = null,
    val salesStartDate: LocalDate? = null,
    val isUniversal: Boolean = false,
    val notes: String? = null,
    val phone: String? = null,
    val appliedInterestRate: BigDecimal? = null,
    val declaredInterestRate: BigDecimal? = null,
    val guaranteedInterestRate: BigDecimal? = null,
    val disclosureUrl: String? = null,
    val expenseRatio: BigDecimal? = null,
)

data class CoverageData(
    val coverageName: String,
    val paymentReason: String,
    val paymentAmount: String,
    val sectionType: String,
)

data class PremiumData(
    val gender: String,
    val age: Int,
    val insurancePeriod: String,
    val paymentPeriod: String,
    val insuranceAmount: Long,
    val premium: Long,
)

data class IndexData(
    val indexType: String,
    val gender: String? = null,
    val coverageName: String? = null,
    val value: BigDecimal,
    val baseDate: LocalDate? = null,
)

data class MetricData(
    val metricType: String,
    val value: BigDecimal,
    val referenceDate: LocalDate,
)

data class SurrenderData(
    val elapsedMonths: Int,
    val gender: String? = null,
    val premiumPaid: Long? = null,
    val surrenderValue: Long,
    val surrenderRate: BigDecimal? = null,
    val policyholderReserve: Long? = null,
)

data class CrawlError(
    val category: String,
    val companyName: String? = null,
    val errorMessage: String,
    val errorType: String,
)
