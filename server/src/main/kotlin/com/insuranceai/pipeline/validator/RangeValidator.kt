package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.ProductRawData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * L2 검증: 값 범위 검증 (보험료, 이율, 지수)
 */
@Component
class RangeValidator {
    private val log = LoggerFactory.getLogger(javaClass)

    fun validate(product: ProductRawData): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        // 보험료 범위: 0 < premium <= 10,000,000
        product.premiumExamples.forEachIndexed { index, premium ->
            if (premium.premium > MAX_PREMIUM) {
                issues.add(
                    ValidationIssue(
                        "L2",
                        "premiumExamples[$index].premium이 범위를 초과합니다: ${premium.premium} (최대 $MAX_PREMIUM)",
                        IssueType.WARNING,
                    ),
                )
            }
        }

        // 지수 범위: index_value >= 0
        product.indices.forEachIndexed { index, indexData ->
            if (indexData.value.toDouble() < 0) {
                issues.add(
                    ValidationIssue("L2", "indices[$index].value가 음수입니다: ${indexData.value}", IssueType.ERROR),
                )
            }
        }

        if (issues.isNotEmpty()) {
            log.warn("L2 검증 이슈: {} - {} ({} 건)", product.companyName, product.productName, issues.size)
        }

        return issues
    }

    companion object {
        const val MAX_PREMIUM = 10_000_000L
        const val MAX_APPLIED_INTEREST_RATE = 20.0
        const val MAX_DECLARED_INTEREST_RATE = 10.0
    }
}
