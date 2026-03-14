package com.insuranceai.search.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.ProductIndexEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
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

    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()

    private val service =
        SearchService(
            keywordSearchService,
            vectorSearchService,
            rankFusionService,
            surrenderValueRepository,
            premiumExampleRepository,
            productIndexRepository,
            insuranceCompanyRepository,
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

    @Test
    fun `searchForRag - 1차 검색 성공 시 폴백 없이 반환`() =
        runTest {
            val companyId = UUID.randomUUID()
            val analysis =
                QuestionAnalysis(
                    companies = listOf("삼성생명"),
                    categories = listOf("PROTECTION"),
                    subcategories = listOf("CANCER"),
                    contentTypes = listOf("EXEMPTION"),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            every { insuranceCompanyRepository.findByNameIn(listOf("삼성생명")) } returns
                flowOf(InsuranceCompanyEntity(id = companyId, name = "삼성생명", type = "LIFE"))

            val keywordResult =
                KeywordSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = productId,
                    productName = "암보험",
                    companyId = companyId,
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "EXEMPTION",
                    articleNumber = "제5조",
                    articleTitle = "면책사항",
                    content = "면책 내용",
                    dataSource = "ASSOCIATION",
                    rank = 1,
                )

            coEvery { keywordSearchService.search(any(), any(), any()) } returns listOf(keywordResult)
            coEvery { vectorSearchService.search(any(), any(), any(), any()) } returns emptyList()

            val results = service.searchForRag("암보험 면책사항", analysis)

            assertEquals(1, results.size)
            // executeHybridSearch 1회만 호출 (폴백 없음)
            io.mockk.coVerify(exactly = 1) { keywordSearchService.search(any(), any(), any()) }
        }

    @Test
    fun `searchForRag - contentTypes 폴백으로 결과 반환`() =
        runTest {
            val analysis =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = listOf("PROTECTION"),
                    subcategories = listOf("CANCER"),
                    contentTypes = listOf("EXEMPTION"),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            val keywordResult =
                KeywordSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = productId,
                    productName = "암보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "COVERAGE",
                    articleNumber = null,
                    articleTitle = null,
                    content = "유사암 제외 보장",
                    dataSource = null,
                    rank = 1,
                )

            // 1차: EXEMPTION 필터 → 0건
            coEvery {
                keywordSearchService.search(any(), match { it.contentTypes != null }, any())
            } returns emptyList()
            coEvery {
                vectorSearchService.search(any(), match { it.contentTypes != null }, any(), any())
            } returns emptyList()

            // 2차: contentTypes 제거 → 결과 있음
            coEvery {
                keywordSearchService.search(any(), match { it.contentTypes == null && it.subcategories != null }, any())
            } returns listOf(keywordResult)
            coEvery {
                vectorSearchService.search(any(), match { it.contentTypes == null && it.subcategories != null }, any(), any())
            } returns emptyList()

            val results = service.searchForRag("유사암 제외", analysis)

            assertEquals(1, results.size)
            assertEquals("유사암 제외 보장", results[0].content)
        }

    @Test
    fun `searchForRag - 모든 폴백 후에도 0건이면 빈 리스트`() =
        runTest {
            val analysis =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = listOf("PROTECTION"),
                    subcategories = listOf("CANCER"),
                    contentTypes = listOf("EXEMPTION"),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            coEvery { keywordSearchService.search(any(), any(), any()) } returns emptyList()
            coEvery { vectorSearchService.search(any(), any(), any(), any()) } returns emptyList()

            val results = service.searchForRag("존재하지 않는 질문", analysis)

            assertTrue(results.isEmpty())
        }

    @Test
    fun `searchForRag - searchKeywords가 있으면 키워드 리스트 검색을 사용한다`() =
        runTest {
            val analysis =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = emptyList(),
                    contentTypes = emptyList(),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                    searchKeywords = listOf("유사암", "제외"),
                )

            val keywordResult =
                KeywordSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = productId,
                    productName = "암보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "COVERAGE",
                    articleNumber = null,
                    articleTitle = null,
                    content = "유사암 제외 보장",
                    dataSource = null,
                    rank = 1,
                )

            coEvery {
                keywordSearchService.searchByKeywords(listOf("유사암", "제외"), any(), any())
            } returns listOf(keywordResult)
            coEvery { vectorSearchService.search(any(), any(), any(), any()) } returns emptyList()

            val results = service.searchForRag("유사암이 제외되는 경우", analysis)

            assertEquals(1, results.size)
            io.mockk.coVerify { keywordSearchService.searchByKeywords(listOf("유사암", "제외"), any(), any()) }
        }

    @Test
    fun `searchForRag - 정상 흐름`() =
        runTest {
            val companyId = UUID.randomUUID()
            val analysis =
                QuestionAnalysis(
                    companies = listOf("삼성생명"),
                    categories = emptyList(),
                    contentTypes = listOf("EXEMPTION"),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            every { insuranceCompanyRepository.findByNameIn(listOf("삼성생명")) } returns
                flowOf(InsuranceCompanyEntity(id = companyId, name = "삼성생명", type = "LIFE"))

            val keywordResult =
                KeywordSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = productId,
                    productName = "종신보험",
                    companyId = companyId,
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "EXEMPTION",
                    articleNumber = "제5조",
                    articleTitle = "면책사항",
                    content = "면책 내용",
                    dataSource = "ASSOCIATION",
                    rank = 1,
                )

            coEvery { keywordSearchService.search("면책", any(), any()) } returns listOf(keywordResult)
            coEvery { vectorSearchService.search("면책", any(), any(), any()) } returns emptyList()

            val results = service.searchForRag("면책", analysis)

            assertEquals(1, results.size)
            assertEquals("삼성생명", results[0].companyName)
        }

    @Test
    fun `searchForRag - 보험사명이 없으면 필터 없이 검색한다`() =
        runTest {
            val analysis =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = emptyList(),
                    contentTypes = emptyList(),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            coEvery { keywordSearchService.search("보험", any(), any()) } returns emptyList()
            coEvery { vectorSearchService.search("보험", any(), any(), any()) } returns emptyList()

            val results = service.searchForRag("보험", analysis)

            assertTrue(results.isEmpty())
        }

    @Test
    fun `searchForRag - 최대 20개만 반환한다`() =
        runTest {
            val analysis =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = emptyList(),
                    contentTypes = emptyList(),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            val keywordResults =
                (1..25).map { rank ->
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

            val results = service.searchForRag("보험", analysis)

            assertEquals(20, results.size)
        }

    @Test
    fun `searchForRag - subQuestions가 있으면 각각 검색 후 통합한다`() =
        runTest {
            val articleId1 = UUID.randomUUID()
            val articleId2 = UUID.randomUUID()
            val analysis =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = emptyList(),
                    contentTypes = emptyList(),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                    subQuestions = listOf("면책사항", "납입면제 조건"),
                )

            coEvery { keywordSearchService.search("면책사항", any(), any()) } returns
                listOf(
                    KeywordSearchResult(
                        articleId = articleId1,
                        productId = productId,
                        productName = "보험A",
                        companyId = UUID.randomUUID(),
                        companyName = "삼성생명",
                        insuranceType = "LIFE",
                        contentType = "EXEMPTION",
                        articleNumber = null,
                        articleTitle = null,
                        content = "면책 내용",
                        dataSource = null,
                        rank = 1,
                    ),
                )
            coEvery { vectorSearchService.search("면책사항", any(), any(), any()) } returns emptyList()

            coEvery { keywordSearchService.search("납입면제 조건", any(), any()) } returns
                listOf(
                    KeywordSearchResult(
                        articleId = articleId2,
                        productId = productId,
                        productName = "보험A",
                        companyId = UUID.randomUUID(),
                        companyName = "삼성생명",
                        insuranceType = "LIFE",
                        contentType = "COVERAGE",
                        articleNumber = null,
                        articleTitle = null,
                        content = "납입면제 내용",
                        dataSource = null,
                        rank = 1,
                    ),
                )
            coEvery { vectorSearchService.search("납입면제 조건", any(), any(), any()) } returns emptyList()

            // 원본 쿼리도 함께 검색 (subQuestions + query)
            coEvery { keywordSearchService.search("면책사항과 납입면제 조건", any(), any()) } returns emptyList()
            coEvery { vectorSearchService.search("면책사항과 납입면제 조건", any(), any(), any()) } returns emptyList()

            val results = service.searchForRag("면책사항과 납입면제 조건", analysis)

            assertEquals(2, results.size)
        }
}
