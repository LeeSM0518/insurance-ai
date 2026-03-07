package com.insuranceai.search.service

import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.SearchFilters
import com.insuranceai.search.repository.PolicyArticleJooqRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeywordSearchServiceTest {
    private val jooqRepository = mockk<PolicyArticleJooqRepository>()
    private val service = KeywordSearchService(jooqRepository)

    @Test
    fun `빈 쿼리는 빈 결과를 반환한다`() =
        runTest {
            val results = service.search("   ")
            assertTrue(results.isEmpty())
        }

    @Test
    fun `특수문자만 있는 쿼리는 빈 결과를 반환한다`() =
        runTest {
            val results = service.search("%%%___")
            assertTrue(results.isEmpty())
        }

    @Test
    fun `쿼리 전처리 - SQL LIKE 특수문자를 제거한다`() {
        assertEquals("면책사항", service.sanitizeQuery("면책%사항"))
        assertEquals("감액", service.sanitizeQuery("감_액"))
        assertEquals("보험", service.sanitizeQuery("보\\험"))
    }

    @Test
    fun `쿼리 전처리 - 특수문자를 제거한다`() {
        assertEquals("면책사항", service.sanitizeQuery("면책!사항@"))
        assertEquals("보험 상품", service.sanitizeQuery("보험 상품"))
    }

    @Test
    fun `쿼리 전처리 - 공백을 정규화한다`() {
        assertEquals("면책 사항", service.sanitizeQuery("면책   사항"))
    }

    @Test
    fun `검색 결과에 하이라이트가 적용된다`() =
        runTest {
            val mockResult =
                KeywordSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = UUID.randomUUID(),
                    productName = "테스트 보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "EXEMPTION",
                    articleNumber = null,
                    articleTitle = null,
                    content = "면책사항에 해당하는 경우 보험금을 지급하지 않습니다",
                    dataSource = null,
                    rank = 1,
                )

            coEvery {
                jooqRepository.searchByKeyword("면책", any(), any())
            } returns listOf(mockResult)

            val results = service.search("면책")

            assertEquals(1, results.size)
            assertTrue(results[0].content.contains("<mark>면책</mark>"))
        }

    @Test
    fun `필터가 전달된다`() =
        runTest {
            val filters = SearchFilters(insuranceType = com.insuranceai.policy.domain.InsuranceType.LIFE)

            coEvery {
                jooqRepository.searchByKeyword("보험", filters, 30)
            } returns emptyList()

            val results = service.search("보험", filters)
            assertTrue(results.isEmpty())
        }
}
