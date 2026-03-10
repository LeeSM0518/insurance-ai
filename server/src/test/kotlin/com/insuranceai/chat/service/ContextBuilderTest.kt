package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.entity.ProductFeatureEntity
import com.insuranceai.policy.entity.ProductMetricEntity
import com.insuranceai.policy.entity.ProductYieldScenarioEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductFeatureRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.ProductYieldScenarioRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import com.insuranceai.search.dto.FusedSearchResult
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
import kotlin.test.assertTrue

class ContextBuilderTest {
    private val surrenderValueRepository = mockk<SurrenderValueRepository>()
    private val premiumExampleRepository = mockk<PremiumExampleRepository>()
    private val productIndexRepository = mockk<ProductIndexRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val productFeatureRepository = mockk<ProductFeatureRepository>()
    private val productYieldScenarioRepository = mockk<ProductYieldScenarioRepository>()
    private val productMetricRepository = mockk<ProductMetricRepository>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()

    private val builder =
        ContextBuilder(
            surrenderValueRepository,
            premiumExampleRepository,
            productIndexRepository,
            productRepository,
            productFeatureRepository,
            productYieldScenarioRepository,
            productMetricRepository,
            insuranceCompanyRepository,
        )

    private val companyId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

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
            name = "테스트 보험",
            category = "PROTECTION",
            renewalType = "비갱신형",
            salesChannel = "대면",
        )

    private fun fusedResult(
        articleId: UUID = UUID.randomUUID(),
        productId: UUID = this.productId,
        companyName: String = "삼성생명",
        content: String = "면책사항에 해당하는 경우 보험금을 지급하지 않습니다",
    ) = FusedSearchResult(
        articleId = articleId,
        productId = productId,
        productName = "테스트 보험",
        companyId = companyId,
        companyName = companyName,
        insuranceType = "LIFE",
        contentType = "COVERAGE",
        articleNumber = "제1조",
        articleTitle = "보장내용",
        content = content,
        dataSource = "ASSOCIATION",
        relevanceScore = 0.8f,
        keywordRank = 1,
        vectorRank = 2,
    )

    private val defaultAnalysis =
        QuestionAnalysis(
            companies = listOf("삼성생명"),
            categories = listOf("CANCER"),
            contentTypes = listOf("COVERAGE"),
            isComparison = false,
            needsSupplementary = SupplementaryNeeds(),
        )

    /** 모든 보충 데이터 repository에 빈 Flow를 반환하도록 설정 */
    private fun stubAllSupplementaryEmpty() {
        every { surrenderValueRepository.findByProductId(any()) } returns emptyFlow()
        every { premiumExampleRepository.findByProductId(any()) } returns emptyFlow()
        every { productIndexRepository.findByProductId(any()) } returns emptyFlow()
        every { productFeatureRepository.findByProductId(any()) } returns emptyFlow()
        every { productYieldScenarioRepository.findByProductId(any()) } returns emptyFlow()
        every { productMetricRepository.findByProductId(any()) } returns emptyFlow()
        coEvery { productRepository.findById(productId) } returns product
        coEvery { insuranceCompanyRepository.findById(companyId) } returns company
    }

    @Test
    fun `빈 검색 결과로 컨텍스트를 구성한다`() =
        runTest {
            val result = builder.build(emptyList(), defaultAnalysis, emptyList())
            assertTrue(result.sources.isEmpty())
        }

    @Test
    fun `검색 결과로 약관 자료 섹션이 구성된다`() =
        runTest {
            stubAllSupplementaryEmpty()

            val searchResults = listOf(fusedResult())
            val result = builder.build(searchResults, defaultAnalysis, emptyList())

            assertTrue(result.contextText.contains("## 검색된 약관 자료"))
            assertTrue(result.contextText.contains("[1]"))
            assertTrue(result.contextText.contains("삼성생명"))
            assertEquals(1, result.sources.size)
        }

    @Test
    fun `보충 데이터가 포함된다`() =
        runTest {
            val analysisWithSupp =
                defaultAnalysis.copy(
                    needsSupplementary = SupplementaryNeeds(surrenderValue = true),
                )

            every { surrenderValueRepository.findByProductId(productId) } returns
                flowOf(
                    SurrenderValueEntity(
                        productId = productId,
                        elapsedMonths = 12,
                        premiumPaid = 100000,
                        surrenderValue = 50000,
                        surrenderRate = BigDecimal("50.0"),
                    ),
                )
            every { premiumExampleRepository.findByProductId(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductId(any()) } returns emptyFlow()
            every { productFeatureRepository.findByProductId(any()) } returns emptyFlow()
            every { productYieldScenarioRepository.findByProductId(any()) } returns emptyFlow()
            every { productMetricRepository.findByProductId(any()) } returns emptyFlow()
            coEvery { productRepository.findById(productId) } returns product
            coEvery { insuranceCompanyRepository.findById(companyId) } returns company

            val result = builder.build(listOf(fusedResult()), analysisWithSupp, emptyList())

            assertTrue(result.contextText.contains("## 보충 정형 데이터"))
            assertTrue(result.contextText.contains("해약환급금"))
        }

    @Test
    fun `대화 이력이 포함된다`() =
        runTest {
            stubAllSupplementaryEmpty()

            val messages =
                listOf(
                    ChatMessageEntity(chatSessionId = UUID.randomUUID(), role = "USER", content = "면책사항 알려주세요"),
                    ChatMessageEntity(chatSessionId = UUID.randomUUID(), role = "ASSISTANT", content = "면책사항은 다음과 같습니다..."),
                )

            val result = builder.build(listOf(fusedResult()), defaultAnalysis, messages)

            assertTrue(result.contextText.contains("## 대화 이력"))
            assertTrue(result.contextText.contains("USER: 면책사항 알려주세요"))
            assertTrue(result.contextText.contains("ASSISTANT: 면책사항은 다음과 같습니다..."))
        }

    @Test
    fun `토큰 추정이 올바르게 계산된다`() {
        val tokens = builder.estimateTokens("가나다") // 3글자 * 1.5 = 4
        assertEquals(4, tokens)
    }

    @Test
    fun `출처 정보가 올바르게 구성된다`() =
        runTest {
            stubAllSupplementaryEmpty()

            val result = builder.build(listOf(fusedResult()), defaultAnalysis, emptyList())

            val source = result.sources[0]
            assertEquals("삼성생명", source.insuranceCompany)
            assertEquals("테스트 보험", source.productName)
            assertEquals("제1조", source.articleNumber)
            assertEquals(0.8f, source.relevanceScore)
        }

    @Test
    fun `상품 기본 정보가 항상 포함된다`() =
        runTest {
            stubAllSupplementaryEmpty()

            // 모든 플래그가 false인 기본 analysis 사용
            val result = builder.build(listOf(fusedResult()), defaultAnalysis, emptyList())

            assertTrue(result.contextText.contains("상품 기본 정보"))
            assertTrue(result.contextText.contains("삼성생명"))
            assertTrue(result.contextText.contains("테스트 보험"))
            assertTrue(result.contextText.contains("카테고리: PROTECTION"))
            assertTrue(result.contextText.contains("갱신유형: 비갱신형"))
        }

    @Test
    fun `productFeature true일 때 상품 특징이 포함된다`() =
        runTest {
            stubAllSupplementaryEmpty()
            every { productFeatureRepository.findByProductId(productId) } returns
                flowOf(
                    ProductFeatureEntity(
                        productId = productId,
                        key = "납입면제",
                        value = "3대 질병 진단 시 납입면제",
                    ),
                )

            val analysis =
                defaultAnalysis.copy(
                    needsSupplementary = SupplementaryNeeds(productFeature = true),
                )

            val result = builder.build(listOf(fusedResult()), analysis, emptyList())

            assertTrue(result.contextText.contains("### 상품 특징"))
            assertTrue(result.contextText.contains("납입면제: 3대 질병 진단 시 납입면제"))
        }

    @Test
    fun `yieldScenario true일 때 수익률 시나리오가 포함된다`() =
        runTest {
            stubAllSupplementaryEmpty()
            every { productYieldScenarioRepository.findByProductId(productId) } returns
                flowOf(
                    ProductYieldScenarioEntity(
                        productId = productId,
                        scenarioType = "OPTIMISTIC",
                        scenarioLabel = "낙관",
                        yieldRate = BigDecimal("5.5"),
                        gender = "M",
                    ),
                )

            val analysis =
                defaultAnalysis.copy(
                    needsSupplementary = SupplementaryNeeds(yieldScenario = true),
                )

            val result = builder.build(listOf(fusedResult()), analysis, emptyList())

            assertTrue(result.contextText.contains("### 수익률 시나리오"))
            assertTrue(result.contextText.contains("낙관"))
            assertTrue(result.contextText.contains("5.5%"))
        }

    @Test
    fun `productMetric true일 때 상품 지표가 포함된다`() =
        runTest {
            stubAllSupplementaryEmpty()
            every { productMetricRepository.findByProductId(productId) } returns
                flowOf(
                    ProductMetricEntity(
                        productId = productId,
                        metricType = "LOSS_RATIO",
                        metricValue = BigDecimal("75.3"),
                        referenceDate = LocalDate.of(2026, 1, 1),
                    ),
                )

            val analysis =
                defaultAnalysis.copy(
                    needsSupplementary = SupplementaryNeeds(productMetric = true),
                )

            val result = builder.build(listOf(fusedResult()), analysis, emptyList())

            assertTrue(result.contextText.contains("### 상품 지표"))
            assertTrue(result.contextText.contains("LOSS_RATIO"))
            assertTrue(result.contextText.contains("75.3"))
        }

    @Test
    fun `모든 새 플래그가 false일 때 기존 동작과 동일하다`() =
        runTest {
            stubAllSupplementaryEmpty()

            val result = builder.build(listOf(fusedResult()), defaultAnalysis, emptyList())

            // 상품 기본 정보는 포함되지만, 새 섹션들은 포함되지 않음
            assertTrue(result.contextText.contains("상품 기본 정보"))
            assertTrue(!result.contextText.contains("### 상품 특징"))
            assertTrue(!result.contextText.contains("### 수익률 시나리오"))
            assertTrue(!result.contextText.contains("### 상품 지표"))
        }
}
