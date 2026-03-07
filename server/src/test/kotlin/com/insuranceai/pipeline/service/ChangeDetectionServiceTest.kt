package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ChangeDetectionServiceTest {
    private val productRepository = mockk<ProductRepository>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val service = ChangeDetectionService(productRepository, insuranceCompanyRepository)

    private val companyId = UUID.randomUUID()
    private val company =
        InsuranceCompanyEntity(
            id = companyId,
            name = "삼성생명",
            type = "LIFE",
            associationCode = "samsung-life",
        )

    @Nested
    inner class DetectChanges {
        @Test
        fun `신규 상품 감지`() =
            runTest {
                coEvery { insuranceCompanyRepository.findByType("LIFE") } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf()

                val crawled =
                    listOf(
                        createProductRawData("삼성생명", "새상품", "checksum1"),
                    )

                val result = service.detectChanges(crawled, InsuranceType.LIFE)
                assertEquals(1, result.newProducts.size)
                assertEquals(0, result.changedProducts.size)
                assertEquals(0, result.deletedProductIds.size)
            }

        @Test
        fun `변경 상품 감지`() =
            runTest {
                val existingProductId = UUID.randomUUID()
                val existingProduct =
                    ProductEntity(
                        id = existingProductId,
                        insuranceCompanyId = companyId,
                        name = "기존상품",
                        category = "종신보험",
                        checksum = "old_checksum",
                    )

                coEvery { insuranceCompanyRepository.findByType("LIFE") } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf(existingProduct)

                val crawled =
                    listOf(
                        createProductRawData("삼성생명", "기존상품", "new_checksum"),
                    )

                val result = service.detectChanges(crawled, InsuranceType.LIFE)
                assertEquals(0, result.newProducts.size)
                assertEquals(1, result.changedProducts.size)
                assertEquals(0, result.deletedProductIds.size)
            }

        @Test
        fun `삭제 상품 감지`() =
            runTest {
                val existingProductId = UUID.randomUUID()
                val existingProduct =
                    ProductEntity(
                        id = existingProductId,
                        insuranceCompanyId = companyId,
                        name = "삭제될상품",
                        category = "종신보험",
                        checksum = "checksum",
                    )

                coEvery { insuranceCompanyRepository.findByType("LIFE") } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf(existingProduct)

                val result = service.detectChanges(emptyList(), InsuranceType.LIFE)
                assertEquals(0, result.newProducts.size)
                assertEquals(0, result.changedProducts.size)
                assertEquals(1, result.deletedProductIds.size)
                assertEquals(existingProductId, result.deletedProductIds.first())
            }

        @Test
        fun `변경 없는 상품은 무시`() =
            runTest {
                val existingProduct =
                    ProductEntity(
                        id = UUID.randomUUID(),
                        insuranceCompanyId = companyId,
                        name = "변경없는상품",
                        category = "종신보험",
                        checksum = "same_checksum",
                    )

                coEvery { insuranceCompanyRepository.findByType("LIFE") } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf(existingProduct)

                val crawled =
                    listOf(
                        createProductRawData("삼성생명", "변경없는상품", "same_checksum"),
                    )

                val result = service.detectChanges(crawled, InsuranceType.LIFE)
                assertEquals(0, result.newProducts.size)
                assertEquals(0, result.changedProducts.size)
                assertEquals(0, result.deletedProductIds.size)
            }
    }

    private fun createProductRawData(
        companyName: String,
        productName: String,
        checksum: String,
    ) = ProductRawData(
        companyName = companyName,
        productName = productName,
        category = "종신보험",
        checksum = checksum,
    )
}
