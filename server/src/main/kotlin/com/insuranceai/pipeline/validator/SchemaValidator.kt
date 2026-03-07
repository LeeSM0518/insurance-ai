package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * L1 검증: 필수 필드 존재 여부 + 타입 검증 + 참조 무결성
 */
@Component
class SchemaValidator(
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 유효한 renewalType 값 목록 */
    private val validRenewalTypes = setOf("갱신형", "비갱신형", null)

    suspend fun validate(product: ProductRawData): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        // 필수 필드 검증
        if (product.productName.isBlank()) {
            issues.add(ValidationIssue("L1", "productName이 비어있습니다", IssueType.ERROR))
        }
        if (product.companyName.isBlank()) {
            issues.add(ValidationIssue("L1", "companyName이 비어있습니다", IssueType.ERROR))
        }

        // 타입 검증: renewalType이 유효한 enum 값인지
        if (product.renewalType != null && product.renewalType !in validRenewalTypes) {
            issues.add(
                ValidationIssue("L1", "잘못된 renewalType: ${product.renewalType}", IssueType.ERROR),
            )
        }

        // 보험료 타입 검증 (음수 불허)
        product.premiumExamples.forEachIndexed { index, premium ->
            if (premium.premium <= 0) {
                issues.add(
                    ValidationIssue("L1", "premiumExamples[$index].premium이 양수가 아닙니다: ${premium.premium}", IssueType.ERROR),
                )
            }
        }

        // 참조 무결성: companyName이 DB에 존재하는지
        if (product.companyName.isNotBlank()) {
            val companies = insuranceCompanyRepository.findByIsActiveTrue().toList()
            val companyNames = companies.map { it.name }.toSet()
            if (product.companyName !in companyNames) {
                issues.add(
                    ValidationIssue("L1", "보험사 '${product.companyName}'이(가) DB에 존재하지 않습니다", IssueType.ERROR),
                )
            }
        }

        if (issues.isNotEmpty()) {
            log.warn("L1 검증 실패: {} - {} ({} 건)", product.companyName, product.productName, issues.size)
        }

        return issues
    }
}

/** 검증 이슈 */
data class ValidationIssue(
    val level: String,
    val message: String,
    val type: IssueType,
)

enum class IssueType {
    ERROR,
    WARNING,
}
