package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.dto.SurrenderData
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsistencyValidatorTest {
    private val validator = ConsistencyValidator()

    private fun product(
        company: String = "삼성생명",
        name: String = "테스트 보험",
        category: String = "종신보험",
        coverages: List<CoverageData> = listOf(CoverageData("사망", "사망 시", "1억원", "MAIN")),
        surrenderValues: List<SurrenderData> = emptyList(),
    ) = ProductRawData(
        companyName = company,
        productName = name,
        category = category,
        coverages = coverages,
        surrenderValues = surrenderValues,
        checksum = "abc123",
    )

    @Test
    fun `중복 상품이 없으면 이슈가 없다`() {
        val products =
            listOf(
                product(name = "보험A"),
                product(name = "보험B"),
            )
        val issues = validator.validate(products)
        assertTrue(issues.none { it.message.contains("중복") })
    }

    @Test
    fun `동일 보험사-상품명-카테고리 중복을 감지한다`() {
        val products =
            listOf(
                product(),
                product(), // 중복
            )
        val issues = validator.validate(products)
        assertTrue(issues.any { it.level == "L3" && it.message.contains("중복") })
    }

    @Test
    fun `다른 카테고리의 같은 상품명은 중복이 아니다`() {
        val products =
            listOf(
                product(category = "종신보험"),
                product(category = "정기보험"),
            )
        val issues = validator.validate(products)
        assertTrue(issues.none { it.message.contains("중복") })
    }

    @Test
    fun `보장성 상품에서 보장내용이 없으면 L3 에러`() {
        val products = listOf(product(coverages = emptyList()))
        val issues = validator.validate(products)
        assertTrue(issues.any { it.level == "L3" && it.type == IssueType.ERROR && it.message.contains("보장내용") })
    }

    @Test
    fun `보장내용이 있으면 에러가 아니다`() {
        val products = listOf(product())
        val issues = validator.validate(products)
        assertTrue(issues.none { it.type == IssueType.ERROR })
    }

    @Test
    fun `저축성 상품은 coverages 없어도 에러가 아니다`() {
        val surrenderData = listOf(SurrenderData(elapsedMonths = 12, surrenderValue = 1_000_000L))
        val products =
            listOf(
                product(category = "SAVINGS", coverages = emptyList(), surrenderValues = surrenderData),
                product(category = "VARIABLE_SAVINGS", coverages = emptyList(), surrenderValues = surrenderData),
                product(category = "PENSION", coverages = emptyList(), surrenderValues = surrenderData),
                product(category = "NL_SAVINGS", coverages = emptyList(), surrenderValues = surrenderData),
                product(category = "NL_PENSION", coverages = emptyList(), surrenderValues = surrenderData),
            )
        val issues = validator.validate(products)
        assertTrue(issues.none { it.type == IssueType.ERROR })
    }

    @Test
    fun `저축성 상품에서 해약환급금이 없으면 경고`() {
        val products = listOf(product(category = "SAVINGS", coverages = emptyList()))
        val issues = validator.validate(products)
        assertTrue(issues.any { it.level == "L3" && it.type == IssueType.WARNING && it.message.contains("해약환급금") })
    }

    @Test
    fun `중복 경고 건수가 정확하다`() {
        val products =
            listOf(
                product(name = "보험A"),
                product(name = "보험A"), // 중복 1
                product(name = "보험A"), // 중복 2
            )
        val issues = validator.validate(products)
        assertEquals(2, issues.count { it.message.contains("중복 상품") })
    }

    @Test
    fun `중복 지수를 감지한다`() {
        val today = LocalDate.now()
        val duplicateIndices =
            listOf(
                IndexData(indexType = "COVERAGE", gender = "남", value = BigDecimal("100.0"), baseDate = today),
                IndexData(indexType = "COVERAGE", gender = "남", value = BigDecimal("100.0"), baseDate = today), // 중복
                IndexData(indexType = "COVERAGE", gender = "여", value = BigDecimal("95.0"), baseDate = today),
            )
        val products =
            listOf(
                product().copy(indices = duplicateIndices),
            )
        val issues = validator.validate(products)
        assertTrue(issues.any { it.message.contains("중복 지수") })
    }

    @Test
    fun `성별이 다른 지수는 중복이 아니다`() {
        val today = LocalDate.now()
        val indices =
            listOf(
                IndexData(indexType = "COVERAGE", gender = "남", value = BigDecimal("100.0"), baseDate = today),
                IndexData(indexType = "COVERAGE", gender = "여", value = BigDecimal("95.0"), baseDate = today),
            )
        val products =
            listOf(
                product().copy(indices = indices),
            )
        val issues = validator.validate(products)
        assertTrue(issues.none { it.message.contains("중복 지수") })
    }
}
