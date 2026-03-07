package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.entity.CrawlStagingEntity
import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class StagingPublisherTest {
    private val productRepository = mockk<ProductRepository>()
    private val policyArticleRepository = mockk<PolicyArticleRepository>()
    private val premiumExampleRepository = mockk<PremiumExampleRepository>()
    private val productIndexRepository = mockk<ProductIndexRepository>()
    private val productMetricRepository = mockk<ProductMetricRepository>()
    private val surrenderValueRepository = mockk<SurrenderValueRepository>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val normalizationService = mockk<NormalizationService>()
    private val crawlStagingRepository = mockk<CrawlStagingRepository>()
    private val crawlStagingMapper = mockk<CrawlStagingMapper>()

    private val publisher =
        StagingPublisher(
            productRepository,
            policyArticleRepository,
            premiumExampleRepository,
            productIndexRepository,
            productMetricRepository,
            surrenderValueRepository,
            insuranceCompanyRepository,
            normalizationService,
            crawlStagingRepository,
            crawlStagingMapper,
        )

    private val companyId = UUID.randomUUID()
    private val company =
        InsuranceCompanyEntity(
            id = companyId,
            name = "삼성생명",
            type = "LIFE",
        )

    @Nested
    inner class Publish {
        @Test
        fun `검증 통과 레코드가 없으면 빈 결과 반환`() =
            runTest {
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-001", "PASSED")
                } returns flowOf()

                val result = publisher.publish("batch-001")

                assertEquals(0, result.productsCreated)
                assertEquals(0, result.productsUpdated)
            }

        @Test
        fun `신규 상품 반영`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "신규상품",
                        category = "종신보험",
                        coverages = listOf(CoverageData("사망", "사망 시", "1억", "MAIN")),
                        checksum = "abc123",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-001",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val newProduct =
                    ProductEntity(
                        insuranceCompanyId = companyId,
                        name = "신규상품",
                        category = "종신보험",
                        checksum = "abc123",
                    )

                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-001", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf()
                every { normalizationService.toProductEntity(rawData, companyId) } returns newProduct
                coEvery { productRepository.save(any()) } answers {
                    firstArg<ProductEntity>().copy(id = UUID.randomUUID())
                }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()

                val result = publisher.publish("batch-001")

                assertEquals(1, result.productsCreated)
                assertEquals(0, result.productsUpdated)
            }
    }

    @Nested
    inner class DeactivateProducts {
        @Test
        fun `상품 비활성화`() =
            runTest {
                val productId = UUID.randomUUID()
                val product =
                    ProductEntity(
                        id = productId,
                        insuranceCompanyId = companyId,
                        name = "삭제상품",
                        category = "정기보험",
                        isActive = true,
                    )

                coEvery { productRepository.findById(productId) } returns product
                coEvery { productRepository.save(any()) } answers { firstArg() }

                publisher.deactivateProducts(listOf(productId))

                coVerify {
                    productRepository.save(
                        match { !it.isActive },
                    )
                }
            }
    }

    @Nested
    inner class PublishArticlesWithVersioning {
        @Test
        fun `기존 조문 비활성화 후 신규 삽입`() =
            runTest {
                val productId = UUID.randomUUID()
                val existingArticle =
                    PolicyArticleEntity(
                        id = UUID.randomUUID(),
                        productId = productId,
                        content = "기존 약관",
                        contentType = "COVERAGE",
                        isActive = true,
                    )
                val newArticle =
                    PolicyArticleEntity(
                        productId = productId,
                        content = "새 약관",
                        contentType = "COVERAGE",
                    )

                coEvery { policyArticleRepository.findByProductId(productId) } returns flowOf(existingArticle)
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }

                publisher.publishArticlesWithVersioning(productId, listOf(newArticle))

                // 기존 비활성화 + 신규 삽입 = 2회 save
                coVerify(exactly = 2) { policyArticleRepository.save(any()) }
            }
    }
}
