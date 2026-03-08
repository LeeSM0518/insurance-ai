package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.ProductRawData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * L3 검증: 중복 감지 + 카테고리별 정합성 검증
 */
@Component
class ConsistencyValidator {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 저축성 카테고리: coverages 대신 surrenderValues로 검증 */
        private val SAVINGS_CATEGORIES =
            setOf(
                "SAVINGS",
                "VARIABLE_SAVINGS",
                "PENSION",
                "NL_SAVINGS",
                "NL_PENSION",
            )
    }

    fun validate(products: List<ProductRawData>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        // 중복 감지: 동일 보험사 + 상품명 + 카테고리
        val seen = mutableSetOf<String>()
        for (product in products) {
            val key = "${product.companyName}|${product.productName}|${product.category}"
            if (!seen.add(key)) {
                issues.add(
                    ValidationIssue(
                        "L3",
                        "중복 상품 감지: ${product.companyName} - ${product.productName} (${product.category}) → upsert 처리",
                        IssueType.WARNING,
                    ),
                )
            }
        }

        // 카테고리별 필수 데이터 검증
        for (product in products) {
            if (product.category in SAVINGS_CATEGORIES) {
                // 저축성/연금: 해약환급금 데이터 검증
                if (product.surrenderValues.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            "L3",
                            "해약환급금 데이터가 없습니다: ${product.companyName} - ${product.productName}",
                            IssueType.WARNING,
                        ),
                    )
                }
            } else {
                // 보장성/실손: 보장내용 최소 1건 검증
                if (product.coverages.isEmpty()) {
                    issues.add(
                        ValidationIssue(
                            "L3",
                            "보장내용이 없습니다: ${product.companyName} - ${product.productName}",
                            IssueType.ERROR,
                        ),
                    )
                }
            }
        }

        if (issues.isNotEmpty()) {
            log.warn("L3 검증 이슈: {} 건", issues.size)
        }

        return issues
    }
}
