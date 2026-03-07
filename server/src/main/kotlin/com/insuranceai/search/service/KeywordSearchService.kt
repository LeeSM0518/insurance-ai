package com.insuranceai.search.service

import com.insuranceai.common.util.TextUtil
import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.SearchFilters
import com.insuranceai.search.repository.PolicyArticleJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * pg_bigm 2-gram 인덱스를 활용한 한글 키워드 검색
 */
@Service
class KeywordSearchService(
    private val policyArticleJooqRepository: PolicyArticleJooqRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 키워드 검색 실행 */
    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters(),
        limit: Int = 30,
    ): List<KeywordSearchResult> {
        val sanitizedQuery = sanitizeQuery(query)
        if (sanitizedQuery.isBlank()) {
            return emptyList()
        }

        log.debug("키워드 검색: query='{}', filters={}", sanitizedQuery, filters)

        val results =
            policyArticleJooqRepository.searchByKeyword(
                query = sanitizedQuery,
                filters = filters,
                limit = limit,
            )

        log.debug("키워드 검색 결과: {} 건", results.size)

        // 스니펫 + 하이라이트 적용
        return results.map { result ->
            val snippet = TextUtil.createSnippet(result.content, sanitizedQuery)
            val highlighted = TextUtil.highlightKeyword(snippet, sanitizedQuery)
            result.copy(content = highlighted)
        }
    }

    /** 쿼리 전처리: 특수문자 제거, 공백 정규화 */
    internal fun sanitizeQuery(query: String): String =
        query
            .replace(Regex("[%_\\\\]"), "") // SQL LIKE 특수문자 이스케이프
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "") // 문자/숫자/공백만 유지
            .let { TextUtil.normalize(it) }
}
