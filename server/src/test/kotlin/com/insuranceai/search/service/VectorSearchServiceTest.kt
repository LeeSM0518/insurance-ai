package com.insuranceai.search.service

import com.insuranceai.pipeline.embedder.EmbeddingService
import com.insuranceai.search.dto.SearchFilters
import com.insuranceai.search.dto.VectorSearchResult
import com.insuranceai.search.repository.VectorSearchJooqRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorSearchServiceTest {
    private val vectorSearchJooqRepository = mockk<VectorSearchJooqRepository>()
    private val embeddingService = mockk<EmbeddingService>()
    private val service = VectorSearchService(vectorSearchJooqRepository, embeddingService)

    @Test
    fun `빈 쿼리는 빈 결과를 반환한다`() =
        runTest {
            val results = service.search("   ")
            assertTrue(results.isEmpty())
        }

    @Test
    fun `임베딩 실패 시 빈 리스트를 반환한다`() =
        runTest {
            coEvery { embeddingService.embed(any()) } throws RuntimeException("API 오류")

            val results = service.search("면책사항")
            assertTrue(results.isEmpty())
        }

    @Test
    fun `임베딩 성공 시 벡터 검색을 실행한다`() =
        runTest {
            val mockVector = FloatArray(1536) { 0.1f }
            val mockResult =
                VectorSearchResult(
                    articleId = UUID.randomUUID(),
                    productId = UUID.randomUUID(),
                    productName = "테스트 보험",
                    companyId = UUID.randomUUID(),
                    companyName = "삼성생명",
                    insuranceType = "LIFE",
                    contentType = "COVERAGE",
                    articleNumber = "제1조",
                    articleTitle = "보장내용",
                    content = "테스트 내용",
                    dataSource = "ASSOCIATION",
                    similarity = 0.85f,
                )

            coEvery { embeddingService.embed("면책사항") } returns mockVector
            coEvery {
                vectorSearchJooqRepository.searchSimilar(mockVector, any(), any(), any())
            } returns listOf(mockResult)

            val results = service.search("면책사항")

            assertEquals(1, results.size)
            assertEquals(0.85f, results[0].similarity)
            coVerify { embeddingService.embed("면책사항") }
        }

    @Test
    fun `필터가 전달된다`() =
        runTest {
            val mockVector = FloatArray(1536) { 0.1f }
            val filters = SearchFilters(insuranceType = com.insuranceai.policy.domain.InsuranceType.LIFE)

            coEvery { embeddingService.embed(any()) } returns mockVector
            coEvery {
                vectorSearchJooqRepository.searchSimilar(mockVector, filters, 50, 0.5f)
            } returns emptyList()

            val results = service.search("보험", filters)
            assertTrue(results.isEmpty())
            coVerify { vectorSearchJooqRepository.searchSimilar(mockVector, filters, 50, 0.5f) }
        }
}
