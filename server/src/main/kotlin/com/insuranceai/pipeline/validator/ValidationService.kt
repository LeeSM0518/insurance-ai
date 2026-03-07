package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.ProductRawData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 검증 통과/실패/경고 분류 결과 */
data class ValidationResult(
    val passed: List<ProductRawData>,
    val failed: List<ValidationFailure>,
    val warnings: List<ValidationWarning>,
    val batchHeld: Boolean,
)

data class ValidationFailure(
    val product: ProductRawData,
    val level: String,
    val reason: String,
)

data class ValidationWarning(
    val product: ProductRawData,
    val level: String,
    val reason: String,
)

/**
 * L1 → L2 → L3 → L4 순서로 검증을 실행하고 결과를 집계합니다.
 */
@Service
class ValidationService(
    private val schemaValidator: SchemaValidator,
    private val rangeValidator: RangeValidator,
    private val consistencyValidator: ConsistencyValidator,
    private val anomalyDetector: AnomalyDetector,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun validate(
        products: List<ProductRawData>,
        batchId: String,
        insuranceType: String,
    ): ValidationResult {
        val passed = mutableListOf<ProductRawData>()
        val failed = mutableListOf<ValidationFailure>()
        val warnings = mutableListOf<ValidationWarning>()

        // L1 + L2: 개별 상품 검증
        for (product in products) {
            val l1Issues = schemaValidator.validate(product)
            val l2Issues = rangeValidator.validate(product)
            val allIssues = l1Issues + l2Issues

            val errors = allIssues.filter { it.type == IssueType.ERROR }
            val warns = allIssues.filter { it.type == IssueType.WARNING }

            if (errors.isNotEmpty()) {
                failed.add(
                    ValidationFailure(
                        product = product,
                        level = errors.first().level,
                        reason = errors.joinToString("; ") { it.message },
                    ),
                )
            } else {
                passed.add(product)
            }

            warns.forEach { w ->
                warnings.add(
                    ValidationWarning(product = product, level = w.level, reason = w.message),
                )
            }
        }

        // L3: 배치 내 정합성 검증
        val l3Issues = consistencyValidator.validate(products)
        l3Issues.forEach { issue ->
            if (issue.type == IssueType.ERROR) {
                // L3 ERROR인 상품을 passed에서 제거하고 failed로 이동
                val affectedProducts =
                    passed.filter { p ->
                        issue.message.contains(p.companyName) && issue.message.contains(p.productName)
                    }
                affectedProducts.forEach { p ->
                    passed.remove(p)
                    failed.add(ValidationFailure(product = p, level = "L3", reason = issue.message))
                }
            } else {
                // L3 WARNING은 경고로 기록 (중복 감지 등)
                warnings.add(
                    ValidationWarning(product = products.first(), level = "L3", reason = issue.message),
                )
            }
        }

        // L4: 배치 전체 이상치 감지
        val anomalyResult = anomalyDetector.detect(insuranceType, products.size)
        if (anomalyResult.batchHeld) {
            log.warn("L4 이상치로 배치 보류: batchId={}, reason={}", batchId, anomalyResult.reason)
        }

        log.info(
            "검증 완료: batchId={}, 통과={}, 실패={}, 경고={}, 배치보류={}",
            batchId,
            passed.size,
            failed.size,
            warnings.size,
            anomalyResult.batchHeld,
        )

        return ValidationResult(
            passed = passed,
            failed = failed,
            warnings = warnings,
            batchHeld = anomalyResult.batchHeld,
        )
    }
}
