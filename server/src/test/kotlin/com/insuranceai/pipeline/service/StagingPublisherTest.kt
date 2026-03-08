package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.FeatureData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.dto.SurrenderData
import com.insuranceai.pipeline.dto.YieldScenarioData
import com.insuranceai.pipeline.entity.CrawlStagingEntity
import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.policy.entity.DataSourceEntity
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.repository.DataSourceRepository
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductFeatureRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.ProductYieldScenarioRepository
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
import java.math.BigDecimal
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
    private val productFeatureRepository = mockk<ProductFeatureRepository>()
    private val productYieldScenarioRepository = mockk<ProductYieldScenarioRepository>()
    private val dataSourceRepository = mockk<DataSourceRepository>()

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
            productFeatureRepository,
            productYieldScenarioRepository,
            dataSourceRepository,
        )

    private val companyId = UUID.randomUUID()
    private val dataSourceId = UUID.randomUUID()
    private val company =
        InsuranceCompanyEntity(
            id = companyId,
            name = "삼성생명",
            type = "LIFE",
        )

    /** publish()에서 DataSource 생성을 위한 공통 mock 설정 */
    private fun setupDataSourceMock() {
        coEvery { dataSourceRepository.save(any()) } answers {
            firstArg<DataSourceEntity>().copy(id = dataSourceId)
        }
    }

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

                setupDataSourceMock()
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
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit

                val result = publisher.publish("batch-001")

                assertEquals(1, result.productsCreated)
                assertEquals(0, result.productsUpdated)
            }

        @Test
        fun `기존 상품 업데이트 시 모든 필드가 갱신된다`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "기존상품",
                        category = "종신보험",
                        renewalType = "비갱신형",
                        salesChannel = "대면",
                        surrenderType = "표준형",
                        productType = "종신보장",
                        isUniversal = true,
                        notes = "보험료 기준 40세",
                        premiumExamples =
                            listOf(
                                PremiumData("남", 40, "종신", "20년납", 100000000, premium = 50000),
                            ),
                        checksum = "newchecksum",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-upd",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val existingProductId = UUID.randomUUID()
                val existingProduct =
                    ProductEntity(
                        id = existingProductId,
                        insuranceCompanyId = companyId,
                        name = "기존상품",
                        category = "종신보험",
                        checksum = "oldchecksum",
                    )

                val updatedProduct =
                    ProductEntity(
                        insuranceCompanyId = companyId,
                        name = "기존상품",
                        category = "종신보험",
                        renewalType = "비갱신형",
                        salesChannel = "대면",
                        surrenderType = "표준형",
                        productType = "종신보장",
                        isUniversal = true,
                        notes = "보험료 기준 40세",
                        minPremium = 50000,
                        checksum = "newchecksum",
                    )

                setupDataSourceMock()
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-upd", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf(existingProduct)
                every { normalizationService.toProductEntity(rawData, companyId) } returns updatedProduct
                coEvery { productRepository.save(any()) } answers { firstArg() }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit

                val result = publisher.publish("batch-upd")

                assertEquals(0, result.productsCreated)
                assertEquals(1, result.productsUpdated)
                // 업데이트 시 모든 필드 반영 확인
                coVerify {
                    productRepository.save(
                        match {
                            it.id == existingProductId &&
                                it.renewalType == "비갱신형" &&
                                it.salesChannel == "대면" &&
                                it.surrenderType == "표준형" &&
                                it.isUniversal &&
                                it.checksum == "newchecksum"
                        },
                    )
                }
            }

        @Test
        fun `보험사 전화번호가 null이면 크롤링 값으로 업데이트된다`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "신규상품",
                        category = "종신보험",
                        checksum = "abc123",
                        phone = "1588-0000",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-phone",
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

                setupDataSourceMock()
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-phone", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery { insuranceCompanyRepository.findById(companyId) } returns company
                coEvery { insuranceCompanyRepository.save(any()) } answers { firstArg() }
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
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit

                publisher.publish("batch-phone")

                coVerify {
                    insuranceCompanyRepository.save(match { it.phone == "1588-0000" })
                }
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
    inner class PublishCoverages {
        @Test
        fun `coverages가 policy_articles COVERAGE로 저장됨`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "종신보험A",
                        category = "종신보험",
                        coverages =
                            listOf(
                                CoverageData("사망", "사망 시", "1억", "MAIN"),
                                CoverageData("재해사망", "재해사망 시", "2억", "RIDER"),
                            ),
                        checksum = "abc123",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-002",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val productId = UUID.randomUUID()
                val newProduct =
                    ProductEntity(
                        insuranceCompanyId = companyId,
                        name = "종신보험A",
                        category = "종신보험",
                        checksum = "abc123",
                    )

                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-002", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                setupDataSourceMock()
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf()
                every { normalizationService.toProductEntity(rawData, companyId) } returns newProduct
                coEvery { productRepository.save(any()) } answers {
                    firstArg<ProductEntity>().copy(id = productId)
                }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit

                val result = publisher.publish("batch-002")

                // coverages 2건 → policy_articles COVERAGE 2건 저장
                assertEquals(2, result.articlesCreated)
                coVerify(exactly = 2) {
                    policyArticleRepository.save(
                        match { it.contentType == "COVERAGE" && it.productId == productId },
                    )
                }
            }
    }

    @Nested
    inner class PublishSurrenderValues {
        @Test
        fun `surrenderValues가 surrender_values 테이블에 저장됨`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "저축보험A",
                        category = "저축보험",
                        coverages = emptyList(),
                        surrenderValues =
                            listOf(
                                SurrenderData(elapsedMonths = 12, surrenderValue = 900000L, gender = "M"),
                                SurrenderData(elapsedMonths = 24, surrenderValue = 1800000L, gender = "M"),
                            ),
                        checksum = "sv123",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-sv",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val productId = UUID.randomUUID()
                val newProduct =
                    ProductEntity(
                        insuranceCompanyId = companyId,
                        name = "저축보험A",
                        category = "저축보험",
                        checksum = "sv123",
                    )

                setupDataSourceMock()
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-sv", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf()
                every { normalizationService.toProductEntity(rawData, companyId) } returns newProduct
                coEvery { productRepository.save(any()) } answers {
                    firstArg<ProductEntity>().copy(id = productId)
                }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit
                coEvery { surrenderValueRepository.save(any()) } answers { firstArg() }

                val result = publisher.publish("batch-sv")

                assertEquals(2, result.surrenderValuesCreated)
                coVerify(exactly = 1) { surrenderValueRepository.deleteByProductId(productId) }
                coVerify(exactly = 2) { surrenderValueRepository.save(any()) }
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

        @Test
        fun `contentTypeFilter 지정 시 해당 타입만 비활성화`() =
            runTest {
                val productId = UUID.randomUUID()
                val coverageArticle =
                    PolicyArticleEntity(
                        id = UUID.randomUUID(),
                        productId = productId,
                        content = "기존 COVERAGE",
                        contentType = "COVERAGE",
                        isActive = true,
                    )
                val exemptionArticle =
                    PolicyArticleEntity(
                        id = UUID.randomUUID(),
                        productId = productId,
                        content = "기존 EXEMPTION",
                        contentType = "EXEMPTION",
                        isActive = true,
                    )
                val newCoverage =
                    PolicyArticleEntity(
                        productId = productId,
                        content = "새 COVERAGE",
                        contentType = "COVERAGE",
                    )

                coEvery { policyArticleRepository.findByProductId(productId) } returns
                    flowOf(coverageArticle, exemptionArticle)
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }

                publisher.publishArticlesWithVersioning(productId, listOf(newCoverage), "COVERAGE")

                // COVERAGE 비활성화 1회 + 신규 삽입 1회 = 2회 save
                coVerify(exactly = 2) { policyArticleRepository.save(any()) }
                // COVERAGE만 비활성화됨 (EXEMPTION은 유지)
                coVerify(exactly = 1) {
                    policyArticleRepository.save(match { !it.isActive && it.contentType == "COVERAGE" })
                }
                // EXEMPTION은 비활성화되지 않음
                coVerify(exactly = 0) {
                    policyArticleRepository.save(match { !it.isActive && it.contentType == "EXEMPTION" })
                }
            }
    }

    @Nested
    inner class PublishFeatures {
        @Test
        fun `features가 product_features 테이블에 저장됨`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "치매보험A",
                        category = "치매보험",
                        coverages = emptyList(),
                        features =
                            listOf(
                                FeatureData("경도치매보장", "Y"),
                                FeatureData("유사암포함", "N"),
                            ),
                        checksum = "feat123",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-feat",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val productId = UUID.randomUUID()
                val newProduct =
                    ProductEntity(
                        insuranceCompanyId = companyId,
                        name = "치매보험A",
                        category = "치매보험",
                        checksum = "feat123",
                    )

                setupDataSourceMock()
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-feat", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf()
                every { normalizationService.toProductEntity(rawData, companyId) } returns newProduct
                coEvery { productRepository.save(any()) } answers {
                    firstArg<ProductEntity>().copy(id = productId)
                }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit
                coEvery { productFeatureRepository.deleteByProductId(any()) } returns Unit
                coEvery { productFeatureRepository.save(any()) } answers { firstArg() }

                publisher.publish("batch-feat")

                coVerify(exactly = 1) { productFeatureRepository.deleteByProductId(productId) }
                coVerify(exactly = 2) { productFeatureRepository.save(any()) }
                // FeatureData.featureKey → ProductFeatureEntity.key 매핑 확인
                coVerify {
                    productFeatureRepository.save(match { it.key == "경도치매보장" && it.value == "Y" })
                }
                coVerify {
                    productFeatureRepository.save(match { it.key == "유사암포함" && it.value == "N" })
                }
            }
    }

    @Nested
    inner class CompanyAliasMapping {
        @Test
        fun `aliases가 있는 보험사는 약칭으로도 매핑된다`() =
            runTest {
                val aliasCompanyId = UUID.randomUUID()
                val companyWithAlias =
                    InsuranceCompanyEntity(
                        id = aliasCompanyId,
                        name = "한화손해보험",
                        type = "NON_LIFE",
                        aliases = listOf("한화손보"),
                    )

                val rawData =
                    ProductRawData(
                        companyName = "한화손보", // 약칭 사용
                        productName = "상해보험A",
                        category = "NL_PROTECTION",
                        coverages = listOf(CoverageData("사망", "사망 시", "1억", "MAIN")),
                        checksum = "alias123",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-alias",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val newProduct =
                    ProductEntity(
                        insuranceCompanyId = aliasCompanyId,
                        name = "상해보험A",
                        category = "NL_PROTECTION",
                        checksum = "alias123",
                    )

                setupDataSourceMock()
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-alias", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(companyWithAlias)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(aliasCompanyId)
                } returns flowOf()
                every { normalizationService.toProductEntity(rawData, aliasCompanyId) } returns newProduct
                coEvery { productRepository.save(any()) } answers {
                    firstArg<ProductEntity>().copy(id = UUID.randomUUID())
                }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit

                val result = publisher.publish("batch-alias")

                // 약칭으로 보험사가 매핑되어 상품이 생성됨
                assertEquals(1, result.productsCreated)
            }
    }

    @Nested
    inner class PublishYieldScenarios {
        @Test
        fun `yieldScenarios가 product_yield_scenarios 테이블에 저장됨`() =
            runTest {
                val rawData =
                    ProductRawData(
                        companyName = "삼성생명",
                        productName = "변액보험A",
                        category = "변액보험",
                        coverages = emptyList(),
                        yieldScenarios =
                            listOf(
                                YieldScenarioData("AVERAGE", "평균 수익률", BigDecimal("3.5"), "M"),
                                YieldScenarioData("HIGH", "최고 수익률", BigDecimal("6.0"), null),
                            ),
                        checksum = "yield123",
                    )

                val stagingEntity =
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-yield",
                        entityType = "PRODUCT",
                        entityData = Json.of("{}"),
                        validationStatus = "PASSED",
                    )

                val productId = UUID.randomUUID()
                val newProduct =
                    ProductEntity(
                        insuranceCompanyId = companyId,
                        name = "변액보험A",
                        category = "변액보험",
                        checksum = "yield123",
                    )

                setupDataSourceMock()
                coEvery {
                    crawlStagingRepository.findByBatchIdAndValidationStatus("batch-yield", "PASSED")
                } returns flowOf(stagingEntity)
                every { crawlStagingMapper.fromStagingEntity(stagingEntity) } returns rawData
                coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
                coEvery {
                    productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                } returns flowOf()
                every { normalizationService.toProductEntity(rawData, companyId) } returns newProduct
                coEvery { productRepository.save(any()) } answers {
                    firstArg<ProductEntity>().copy(id = productId)
                }
                coEvery { premiumExampleRepository.findByProductId(any()) } returns flowOf()
                coEvery { productIndexRepository.findByProductId(any()) } returns flowOf()
                coEvery { productMetricRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.findByProductId(any()) } returns flowOf()
                coEvery { policyArticleRepository.save(any()) } answers { firstArg() }
                coEvery { surrenderValueRepository.deleteByProductId(any()) } returns Unit
                coEvery { productYieldScenarioRepository.deleteByProductId(any()) } returns Unit
                coEvery { productYieldScenarioRepository.save(any()) } answers { firstArg() }

                publisher.publish("batch-yield")

                coVerify(exactly = 1) { productYieldScenarioRepository.deleteByProductId(productId) }
                coVerify(exactly = 2) { productYieldScenarioRepository.save(any()) }
                coVerify {
                    productYieldScenarioRepository.save(
                        match { it.scenarioType == "AVERAGE" && it.scenarioLabel == "평균 수익률" && it.gender == "M" },
                    )
                }
            }
    }
}
