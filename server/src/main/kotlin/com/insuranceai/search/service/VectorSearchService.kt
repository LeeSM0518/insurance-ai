package com.insuranceai.search.service

import com.insuranceai.pipeline.embedder.EmbeddingService
import com.insuranceai.search.dto.SearchFilters
import com.insuranceai.search.dto.VectorSearchResult
import com.insuranceai.search.repository.VectorSearchJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VectorSearchService(
    private val vectorSearchJooqRepository: VectorSearchJooqRepository,
    private val embeddingService: EmbeddingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 쿼리 텍스트를 임베딩 변환 후 벡터 검색 실행 */
    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters(),
        limit: Int = 50,
        minSimilarity: Float = 0.4f,
    ): List<VectorSearchResult> {
        if (query.isBlank()) return emptyList()

        val queryVector =
            try {
                embeddingService.embed(query)
            } catch (e: Exception) {
                log.error("임베딩 변환 실패: ${e.message}", e)
                return emptyList()
            }

        log.debug("벡터 검색: query='{}', filters={}", query, filters)

        val results =
            vectorSearchJooqRepository.searchSimilar(
                queryVector = queryVector,
                filters = filters,
                limit = limit,
                minSimilarity = minSimilarity,
            )

        log.debug("벡터 검색 결과: {} 건", results.size)
        return results
    }
}
