package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.ProductRawData
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
    ) = ProductRawData(
        companyName = company,
        productName = name,
        category = category,
        coverages = coverages,
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
    fun `보장내용이 없는 상품은 L3 에러`() {
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
    fun `중복 경고 건수가 정확하다`() {
        val products =
            listOf(
                product(name = "보험A"),
                product(name = "보험A"), // 중복 1
                product(name = "보험A"), // 중복 2
            )
        val issues = validator.validate(products)
        assertEquals(2, issues.count { it.message.contains("중복") })
    }
}
