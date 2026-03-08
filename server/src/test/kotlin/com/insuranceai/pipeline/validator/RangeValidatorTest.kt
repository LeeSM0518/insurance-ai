package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

class RangeValidatorTest {
    private val validator = RangeValidator()

    private fun validProduct() =
        ProductRawData(
            companyName = "삼성생명",
            productName = "테스트 보험",
            category = "종신보험",
            coverages = listOf(CoverageData("사망", "사망 시", "1억원", "MAIN")),
            checksum = "abc123",
        )

    @Test
    fun `정상 범위 데이터는 이슈가 없다`() {
        val issues = validator.validate(validProduct())
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `보험료가 최대값을 초과하면 L2 경고`() {
        val product =
            validProduct().copy(
                premiumExamples =
                    listOf(
                        PremiumData("남", 30, "20년", "20년", 100000000, premium = 20_000_000),
                    ),
            )
        val issues = validator.validate(product)
        assertTrue(issues.any { it.level == "L2" && it.type == IssueType.WARNING })
    }

    @Test
    fun `보험료가 최대값과 같으면 정상`() {
        val product =
            validProduct().copy(
                premiumExamples =
                    listOf(
                        PremiumData("남", 30, "20년", "20년", 100000000, premium = 10_000_000),
                    ),
            )
        val issues = validator.validate(product)
        assertTrue(issues.none { it.message.contains("premium") })
    }

    @Test
    fun `음수 지수는 L2 에러`() {
        val product =
            validProduct().copy(
                indices = listOf(IndexData("합산비율", null, null, BigDecimal("-5.0"))),
            )
        val issues = validator.validate(product)
        assertTrue(issues.any { it.level == "L2" && it.type == IssueType.ERROR })
    }

    @Test
    fun `0 이상 지수는 정상`() {
        val product =
            validProduct().copy(
                indices = listOf(IndexData("합산비율", null, null, BigDecimal("0.0"))),
            )
        val issues = validator.validate(product)
        assertTrue(issues.none { it.level == "L2" && it.type == IssueType.ERROR })
    }
}
