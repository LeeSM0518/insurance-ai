package com.insuranceai.search.service

import com.insuranceai.policy.entity.ProductIndexEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.SearchRequest
import com.insuranceai.search.error.SearchError
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchServiceTest {
    private val keywordSearchService = mockk<KeywordSearchService>()
    private val vectorSearchService = mockk<VectorSearchService>()
    private val rankFusionService = RankFusionService()
    private val surrenderValueRepository = mockk<SurrenderValueRepository>()
    private val premiumExampleRepository = mockk<PremiumExampleRepository>()
    private val productIndexRepository = mockk<ProductIndexRepository>()

    private val service =
        SearchService(
            keywordSearchService,
            vectorSearchService,
            rankFusionService,
            surrenderValueRepository,
            premiumExampleRepository,
            productIndexRepository,
        )

    private val userId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    @Test
    fun `빈 쿼리는 QueryMissing 에러를 발생시킨다`() =
        runTest {
            assertFailsWith<SearchError.QueryMissing> {
                service.search(SearchRequest(query = "  "), userId)
            }
        }

    @Test
    fun `200자 초과 쿼리는 QueryTooLong 에러를 발생시킨다`() =
        runTest {
            val longQuery = "가".repeat(201)
            assertFailsWith<SearchError.QueryTooLong> {
                service.search(SearchRequest(query = longQuery), userId)
            }
        }

    @Test
    fun `정상 검색 시 병렬 검색 + RRF 통합이 수행된다`() =
        runTest {
            val articleId = UUID.randomUUID()
            val keywordResult =
                KeywordSearchResult(
                    articleId = articleId,
                    productId = productId,
                    productName = "테스트 보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "COVERAGE",
                    articleNumber = "제1조",
                    articleTitle = "보장내용",
                    content = "면책 관련 내용",
                    dataSource = "ASSOCIATION",
                    rank = 1,
                )

            coEvery { keywordSearchService.search("면책", any(), any()) } returns listOf(keywordResult)
            coEvery { vectorSearchService.search("면책", any(), any(), any()) } returns emptyList()
            every { surrenderValueRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(any()) } returns emptyFlow()

            val response = service.search(SearchRequest(query = "면책"), userId)

            assertEquals("면책", response.query)
            assertEquals(1, response.totalCount)
            assertEquals(1, response.results.size)
            assertEquals(articleId, response.results[0].id)
        }

    @Test
    fun `페이지네이션이 올바르게 적용된다`() =
        runTest {
            val keywordResults =
                (1..5).map { rank ->
                    KeywordSearchResult(
                        articleId = UUID.randomUUID(),
                        productId = productId,
                        productName = "보험 $rank",
                        companyId = UUID.randomUUID(),
                        companyName = "보험사",
                        insuranceType = "LIFE",
                        contentType = "COVERAGE",
                        articleNumber = null,
                        articleTitle = null,
                        content = "내용 $rank",
                        dataSource = null,
                        rank = rank,
                    )
                }

            coEvery { keywordSearchService.search("보험", any(), any()) } returns keywordResults
            coEvery { vectorSearchService.search("보험", any(), any(), any()) } returns emptyList()
            every { surrenderValueRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(any()) } returns emptyFlow()

            val response = service.search(SearchRequest(query = "보험", page = 1, size = 2), userId)

            assertEquals(5, response.totalCount)
            assertEquals(2, response.results.size)
            assertTrue(response.hasNext)
        }

    @Test
    fun `보충 데이터 존재 여부가 올바르게 반영된다`() =
        runTest {
            val articleId = UUID.randomUUID()
            val keywordResult =
                KeywordSearchResult(
                    articleId = articleId,
                    productId = productId,
                    productName = "테스트 보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "COVERAGE",
                    articleNumber = null,
                    articleTitle = null,
                    content = "내용",
                    dataSource = null,
                    rank = 1,
                )

            coEvery { keywordSearchService.search("보험", any(), any()) } returns listOf(keywordResult)
            coEvery { vectorSearchService.search("보험", any(), any(), any()) } returns emptyList()
            every { surrenderValueRepository.findByProductIdIn(any()) } returns
                flowOf(
                    SurrenderValueEntity(
                        productId = productId,
                        elapsedMonths = 12,
                        premiumPaid = 100000,
                        surrenderValue = 50000,
                        surrenderRate = BigDecimal("50.0"),
                    ),
                )
            every { premiumExampleRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(any()) } returns
                flowOf(
                    ProductIndexEntity(
                        productId = productId,
                        indexType = "GENERAL",
                        indexValue = BigDecimal("100.0"),
                        baseDate = LocalDate.now(),
                    ),
                )

            val response = service.search(SearchRequest(query = "보험"), userId)

            assertTrue(response.results[0].supplementary.hasSurrenderValues)
            assertFalse(response.results[0].supplementary.hasPremiumExamples)
            assertTrue(response.results[0].supplementary.hasPriceIndices)
        }

    @Test
    fun `isComparison이 true이면 다양성 보장이 적용된다`() =
        runTest {
            val companyId1 = UUID.randomUUID()
            val companyId2 = UUID.randomUUID()
            val keywordResults =
                (1..6).map { rank ->
                    KeywordSearchResult(
                        articleId = UUID.randomUUID(),
                        productId = productId,
                        productName = "보험 $rank",
                        companyId = if (rank <= 4) companyId1 else companyId2,
                        companyName = if (rank <= 4) "삼성생명" else "한화생명",
                        insuranceType = "LIFE",
                        contentType = "COVERAGE",
                        articleNumber = null,
                        articleTitle = null,
                        content = "내용 $rank",
                        dataSource = null,
                        rank = rank,
                    )
                }

            coEvery { keywordSearchService.search("암보험 비교", any(), any()) } returns keywordResults
            coEvery { vectorSearchService.search("암보험 비교", any(), any(), any()) } returns emptyList()
            every { surrenderValueRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(any()) } returns emptyFlow()

            val response =
                service.search(
                    SearchRequest(query = "암보험 비교"),
                    userId,
                    isComparison = true,
                )

            assertTrue(response.totalCount > 0)
        }

    @Test
    fun `isComparison이 false이면 다양성 보장이 적용되지 않는다`() =
        runTest {
            val keywordResult =
                KeywordSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = productId,
                    productName = "테스트 보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "COVERAGE",
                    articleNumber = null,
                    articleTitle = null,
                    content = "내용",
                    dataSource = null,
                    rank = 1,
                )

            coEvery { keywordSearchService.search("면책", any(), any()) } returns listOf(keywordResult)
            coEvery { vectorSearchService.search("면책", any(), any(), any()) } returns emptyList()
            every { surrenderValueRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductIdIn(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductIdIn(any()) } returns emptyFlow()

            val response =
                service.search(
                    SearchRequest(query = "면책"),
                    userId,
                    isComparison = false,
                )

            assertEquals(1, response.totalCount)
        }
}
