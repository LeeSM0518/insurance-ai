package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationServiceTest {
    private val companyRepo = mockk<InsuranceCompanyRepository>()
    private val crawlHistoryRepo = mockk<com.insuranceai.pipeline.repository.CrawlHistoryRepository>()

    private val schemaValidator = SchemaValidator(companyRepo)
    private val rangeValidator = RangeValidator()
    private val consistencyValidator = ConsistencyValidator()
    private val anomalyDetector = AnomalyDetector(crawlHistoryRepo)

    private val service = ValidationService(schemaValidator, rangeValidator, consistencyValidator, anomalyDetector)

    private fun setup() {
        every { companyRepo.findByIsActiveTrue() } returns
            flowOf(
                InsuranceCompanyEntity(id = UUID.randomUUID(), name = "삼성생명", type = "LIFE"),
            )
        every {
            crawlHistoryRepo.findByInsuranceTypeOrderByCreatedAtDesc(any())
        } returns flowOf()
    }

    private fun validProduct(name: String = "테스트 보험") =
        ProductRawData(
            companyName = "삼성생명",
            productName = name,
            category = "종신보험",
            coverages = listOf(CoverageData("사망", "사망 시", "1억원", "MAIN")),
            checksum = "abc123",
        )

    @Test
    fun `정상 데이터는 모두 통과한다`() =
        runTest {
            setup()
            val result = service.validate(listOf(validProduct()), "batch-1", "LIFE")

            assertEquals(1, result.passed.size)
            assertTrue(result.failed.isEmpty())
            assertFalse(result.batchHeld)
        }

    @Test
    fun `L1 실패 상품은 failed에 포함된다`() =
        runTest {
            setup()
            val invalidProduct = validProduct().copy(productName = "")
            val result = service.validate(listOf(invalidProduct), "batch-1", "LIFE")

            assertTrue(result.passed.isEmpty())
            assertEquals(1, result.failed.size)
            assertEquals("L1", result.failed[0].level)
        }

    @Test
    fun `여러 상품 중 일부만 실패한다`() =
        runTest {
            setup()
            val products =
                listOf(
                    validProduct("정상 보험"),
                    validProduct("").copy(productName = ""),
                )
            val result = service.validate(products, "batch-1", "LIFE")

            assertEquals(1, result.passed.size)
            assertEquals(1, result.failed.size)
        }

    @Test
    fun `L4 이상치 감지 시 batchHeld가 true`() =
        runTest {
            every { companyRepo.findByIsActiveTrue() } returns
                flowOf(
                    InsuranceCompanyEntity(id = UUID.randomUUID(), name = "삼성생명", type = "LIFE"),
                )
            every {
                crawlHistoryRepo.findByInsuranceTypeOrderByCreatedAtDesc("LIFE")
            } returns
                flowOf(
                    com.insuranceai.pipeline.entity.CrawlHistoryEntity(
                        batchId = "prev",
                        insuranceType = "LIFE",
                        totalProducts = 100,
                        successCount = 100,
                        failureCount = 0,
                    ),
                )

            // 100 → 50 = 50% 감소 → 30% 임계값 초과
            val products = (1..50).map { validProduct("보험$it") }
            val result = service.validate(products, "batch-2", "LIFE")

            assertTrue(result.batchHeld)
        }
}
