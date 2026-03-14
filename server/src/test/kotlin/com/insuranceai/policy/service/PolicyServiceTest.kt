package com.insuranceai.policy.service

import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.policy.entity.DataSourceEntity
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.PremiumExampleEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.entity.ProductFeatureEntity
import com.insuranceai.policy.entity.ProductIndexEntity
import com.insuranceai.policy.entity.ProductMetricEntity
import com.insuranceai.policy.entity.ProductYieldScenarioEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import com.insuranceai.policy.error.PolicyError
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyServiceTest {
    private val policyArticleRepository = mockk<PolicyArticleRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val dataSourceRepository = mockk<DataSourceRepository>()
    private val surrenderValueRepository = mockk<SurrenderValueRepository>()
    private val premiumExampleRepository = mockk<PremiumExampleRepository>()
    private val productIndexRepository = mockk<ProductIndexRepository>()
    private val productFeatureRepository = mockk<ProductFeatureRepository>()
    private val productYieldScenarioRepository = mockk<ProductYieldScenarioRepository>()
    private val productMetricRepository = mockk<ProductMetricRepository>()

    private val service =
        PolicyService(
            policyArticleRepository,
            productRepository,
            insuranceCompanyRepository,
            dataSourceRepository,
            surrenderValueRepository,
            premiumExampleRepository,
            productIndexRepository,
            productFeatureRepository,
            productYieldScenarioRepository,
            productMetricRepository,
        )

    private val companyId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val articleId = UUID.randomUUID()
    private val dataSourceId = UUID.randomUUID()

    private val company =
        InsuranceCompanyEntity(
            id = companyId,
            name = "삼성생명",
            type = "LIFE",
        )

    private val product =
        ProductEntity(
            id = productId,
            insuranceCompanyId = companyId,
            name = "종신보험",
            category = "PROTECTION",
        )

    private val article =
        PolicyArticleEntity(
            id = articleId,
            productId = productId,
            dataSourceId = dataSourceId,
            articleNumber = "제1조",
            articleTitle = "보장내용",
            content = "보험금 지급 사유",
            contentType = "COVERAGE",
            sectionType = null,
            effectiveDate = LocalDate.of(2026, 1, 1),
        )

    @Test
    fun `getArticleDetail - 정상 조회`() =
        runTest {
            coEvery { policyArticleRepository.findById(articleId) } returns article
            coEvery { productRepository.findById(productId) } returns product
            coEvery { insuranceCompanyRepository.findById(companyId) } returns company
            coEvery { dataSourceRepository.findById(dataSourceId) } returns
                DataSourceEntity(id = dataSourceId, sourceType = "ASSOCIATION")
            coEvery { dataSourceRepository.findProductSummaryByProductId(productId) } returns null
            every { policyArticleRepository.findByProductId(productId) } returns
                flowOf(article)

            val result = service.getArticleDetail(articleId)

            assertEquals(articleId, result.id)
            assertEquals("삼성생명", result.insuranceCompany.name)
            assertEquals("종신보험", result.productName)
            assertEquals("COVERAGE", result.contentType)
            assertEquals("ASSOCIATION", result.dataSource)
            assertTrue(result.relatedArticles.isEmpty()) // 자기 자신 제외
        }

    @Test
    fun `getArticleDetail - 조문이 존재하지 않으면 ArticleNotFound`() =
        runTest {
            coEvery { policyArticleRepository.findById(any()) } returns null

            assertFailsWith<PolicyError.ArticleNotFound> {
                service.getArticleDetail(UUID.randomUUID())
            }
        }

    @Test
    fun `getArticleDetail - 관련 조문이 포함된다`() =
        runTest {
            val relatedId = UUID.randomUUID()
            val relatedArticle =
                article.copy(
                    id = relatedId,
                    articleNumber = "제2조",
                    articleTitle = "면책사항",
                    contentType = "EXEMPTION",
                )

            coEvery { policyArticleRepository.findById(articleId) } returns article
            coEvery { productRepository.findById(productId) } returns product
            coEvery { insuranceCompanyRepository.findById(companyId) } returns company
            coEvery { dataSourceRepository.findById(dataSourceId) } returns null
            every { policyArticleRepository.findByProductId(productId) } returns
                flowOf(article, relatedArticle)

            val result = service.getArticleDetail(articleId)

            assertEquals(1, result.relatedArticles.size)
            assertEquals(relatedId, result.relatedArticles[0].id)
            assertEquals("EXEMPTION", result.relatedArticles[0].contentType)
        }

    @Test
    fun `getSurrenderValues - 정상 조회`() =
        runTest {
            every { surrenderValueRepository.findByProductId(productId) } returns
                flowOf(
                    SurrenderValueEntity(
                        productId = productId,
                        elapsedMonths = 12,
                        premiumPaid = 1200000,
                        surrenderValue = 600000,
                        surrenderRate = BigDecimal("50.0"),
                    ),
                )

            val result = service.getSurrenderValues(productId)

            assertEquals(productId, result.productId)
            assertEquals(1, result.values.size)
            assertEquals(12, result.values[0].elapsedMonths)
            assertEquals(600000L, result.values[0].surrenderValue)
        }

    @Test
    fun `getPremiumExamples - 정상 조회`() =
        runTest {
            every { premiumExampleRepository.findByProductId(productId) } returns
                flowOf(
                    PremiumExampleEntity(
                        productId = productId,
                        gender = "M",
                        age = 40,
                        insurancePeriod = "종신",
                        paymentPeriod = "20년납",
                        insuranceAmount = 100000000,
                        premium = 45000,
                    ),
                )

            val result = service.getPremiumExamples(productId)

            assertEquals(1, result.examples.size)
            assertEquals("M", result.examples[0].gender)
            assertEquals(45000L, result.examples[0].premium)
        }

    @Test
    fun `getProductIndices - 정상 조회`() =
        runTest {
            every { productIndexRepository.findByProductId(productId) } returns
                flowOf(
                    ProductIndexEntity(
                        productId = productId,
                        indexType = "GENERAL",
                        indexValue = BigDecimal("100.0"),
                        baseDate = LocalDate.of(2026, 1, 1),
                    ),
                )

            val result = service.getProductIndices(productId)

            assertEquals(1, result.indices.size)
            assertEquals("GENERAL", result.indices[0].indexType)
        }

    @Test
    fun `getSupplementaryData - 병렬 조회`() =
        runTest {
            val ids = listOf(productId)
            every { surrenderValueRepository.findByProductIdIn(ids) } returns
                flowOf(
                    SurrenderValueEntity(
                        productId = productId,
                        elapsedMonths = 12,
                        surrenderValue = 600000,
                    ),
                )
            every { premiumExampleRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productFeatureRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productYieldScenarioRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productMetricRepository.findByProductIdIn(ids) } returns emptyFlow()

            val result =
                service.getSupplementaryData(
                    ids,
                    SupplementaryNeeds(surrenderValue = true, premiumExample = false, priceIndex = false),
                )

            assertEquals(1, result.surrenderValues.size)
            assertTrue(result.premiumExamples.isEmpty())
            assertTrue(result.priceIndices.isEmpty())
            assertTrue(result.productFeatures.isEmpty())
            assertTrue(result.yieldScenarios.isEmpty())
            assertTrue(result.productMetrics.isEmpty())
        }

    @Test
    fun `getPremiumExamples - nullable insuranceAmount와 text 필드`() =
        runTest {
            every { premiumExampleRepository.findByProductId(productId) } returns
                flowOf(
                    PremiumExampleEntity(
                        productId = productId,
                        gender = "남",
                        age = 60,
                        insurancePeriod = "1년만기",
                        paymentPeriod = "1년납",
                        insuranceAmount = null,
                        insuranceAmountText = "용어해설참조",
                        premium = 15000,
                    ),
                )

            val result = service.getPremiumExamples(productId)

            assertEquals(1, result.examples.size)
            assertNull(result.examples[0].insuranceAmount)
            assertEquals("용어해설참조", result.examples[0].insuranceAmountText)
        }

    @Test
    fun `getSupplementaryData - 빈 productIds`() =
        runTest {
            val result =
                service.getSupplementaryData(
                    emptyList(),
                    SupplementaryNeeds(surrenderValue = true),
                )

            assertTrue(result.surrenderValues.isEmpty())
        }

    @Test
    fun `getSupplementaryData - 새 데이터 병렬 조회`() =
        runTest {
            val ids = listOf(productId)
            every { surrenderValueRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { premiumExampleRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productFeatureRepository.findByProductIdIn(ids) } returns
                flowOf(
                    ProductFeatureEntity(
                        productId = productId,
                        key = "납입면제",
                        value = "3대 질병 진단 시",
                    ),
                )
            every { productYieldScenarioRepository.findByProductIdIn(ids) } returns
                flowOf(
                    ProductYieldScenarioEntity(
                        productId = productId,
                        scenarioType = "STANDARD",
                        scenarioLabel = "표준",
                        yieldRate = BigDecimal("3.5"),
                        gender = "M",
                    ),
                )
            every { productMetricRepository.findByProductIdIn(ids) } returns
                flowOf(
                    ProductMetricEntity(
                        productId = productId,
                        metricType = "LOSS_RATIO",
                        metricValue = BigDecimal("75.3"),
                        referenceDate = LocalDate.of(2026, 1, 1),
                    ),
                )

            val result =
                service.getSupplementaryData(
                    ids,
                    SupplementaryNeeds(productFeature = true, yieldScenario = true, productMetric = true),
                )

            assertEquals(1, result.productFeatures.size)
            assertEquals("납입면제", result.productFeatures[0].key)
            assertEquals(1, result.yieldScenarios.size)
            assertEquals("표준", result.yieldScenarios[0].scenarioLabel)
            assertEquals(1, result.productMetrics.size)
            assertEquals("LOSS_RATIO", result.productMetrics[0].metricType)
        }

    @Test
    fun `getSupplementaryData - 새 플래그가 false이면 빈 리스트 반환`() =
        runTest {
            val ids = listOf(productId)
            every { surrenderValueRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { premiumExampleRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productFeatureRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productYieldScenarioRepository.findByProductIdIn(ids) } returns emptyFlow()
            every { productMetricRepository.findByProductIdIn(ids) } returns emptyFlow()

            val result =
                service.getSupplementaryData(
                    ids,
                    SupplementaryNeeds(), // 모두 false
                )

            assertTrue(result.productFeatures.isEmpty())
            assertTrue(result.yieldScenarios.isEmpty())
            assertTrue(result.productMetrics.isEmpty())
        }
}
