package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaValidatorTest {
    private val companyRepo = mockk<InsuranceCompanyRepository>()
    private val validator = SchemaValidator(companyRepo)

    private fun mockActiveCompanies(vararg names: String) {
        val entities =
            names.map {
                InsuranceCompanyEntity(id = UUID.randomUUID(), name = it, type = "LIFE")
            }
        every { companyRepo.findByIsActiveTrue() } returns flowOf(*entities.toTypedArray())
    }

    private fun validProduct(
        companyName: String = "삼성생명",
        productName: String = "테스트 보험",
    ) = ProductRawData(
        companyName = companyName,
        productName = productName,
        category = "종신보험",
        coverages = listOf(CoverageData("사망", "사망 시", "1억원", "MAIN")),
        checksum = "abc123",
    )

    @Test
    fun `정상 데이터는 이슈가 없다`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val issues = validator.validate(validProduct())
            assertTrue(issues.isEmpty())
        }

    @Test
    fun `productName이 빈 문자열이면 L1 에러`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val issues = validator.validate(validProduct(productName = ""))
            assertTrue(issues.any { it.level == "L1" && it.message.contains("productName") })
        }

    @Test
    fun `companyName이 빈 문자열이면 L1 에러`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val issues = validator.validate(validProduct(companyName = ""))
            assertTrue(issues.any { it.level == "L1" && it.message.contains("companyName") })
        }

    @Test
    fun `DB에 없는 보험사이면 L1 에러`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val issues = validator.validate(validProduct(companyName = "없는보험사"))
            assertTrue(issues.any { it.level == "L1" && it.message.contains("존재하지 않습니다") })
        }

    @Test
    fun `음수 보험료는 L1 에러`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val product =
                validProduct().copy(
                    premiumExamples =
                        listOf(
                            PremiumData("남", 30, "20년", "20년", 100000000, -1000),
                        ),
                )
            val issues = validator.validate(product)
            assertTrue(issues.any { it.level == "L1" && it.message.contains("양수") })
        }

    @Test
    fun `잘못된 renewalType은 L1 에러`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val product = validProduct().copy(renewalType = "잘못된값")
            val issues = validator.validate(product)
            assertTrue(issues.any { it.level == "L1" && it.message.contains("renewalType") })
        }

    @Test
    fun `NON_RENEWAL renewalType은 정상이다`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val product = validProduct().copy(renewalType = "NON_RENEWAL")
            val issues = validator.validate(product)
            assertTrue(issues.none { it.message.contains("renewalType") })
        }

    @Test
    fun `RENEWAL renewalType은 정상이다`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val product = validProduct().copy(renewalType = "RENEWAL")
            val issues = validator.validate(product)
            assertTrue(issues.none { it.message.contains("renewalType") })
        }

    @Test
    fun `null renewalType은 에러가 아니다`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val product = validProduct().copy(renewalType = null)
            val issues = validator.validate(product)
            assertTrue(issues.none { it.message.contains("renewalType") })
        }

    @Test
    fun `에러 건수가 정확히 집계된다`() =
        runTest {
            mockActiveCompanies("삼성생명")
            val product = validProduct(companyName = "", productName = "")
            val issues = validator.validate(product)
            assertEquals(2, issues.count { it.type == IssueType.ERROR })
        }
}
