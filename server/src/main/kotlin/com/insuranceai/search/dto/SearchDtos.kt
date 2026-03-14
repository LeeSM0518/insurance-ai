package com.insuranceai.search.dto

import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.InsuranceType
import java.util.UUID

/** 검색 필터 */
data class SearchFilters(
    val companyIds: List<UUID>? = null,
    val insuranceType: InsuranceType? = null,
    val categories: List<String>? = null,
    val subcategories: List<String>? = null,
    val contentTypes: List<ContentType>? = null,
)

/** 키워드 검색 결과 */
data class KeywordSearchResult(
    val articleId: UUID,
    val productId: UUID,
    val productName: String,
    val companyId: UUID,
    val companyName: String,
    val insuranceType: String,
    val contentType: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val content: String,
    val dataSource: String?,
    val dataSourceId: UUID? = null,
    val rank: Int,
    val matchQuality: Float = 0f,
)

/** 벡터 검색 결과 */
data class VectorSearchResult(
    val articleId: UUID,
    val productId: UUID,
    val productName: String,
    val companyId: UUID,
    val companyName: String,
    val insuranceType: String,
    val contentType: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val content: String,
    val dataSource: String?,
    val dataSourceId: UUID? = null,
    val similarity: Float,
)

/** 하이브리드 검색 통합 결과 (Convex Combination) */
data class FusedSearchResult(
    val articleId: UUID,
    val productId: UUID,
    val productName: String,
    val companyId: UUID,
    val companyName: String,
    val insuranceType: String,
    val contentType: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val content: String,
    val dataSource: String?,
    val dataSourceId: UUID? = null,
    val relevanceScore: Float,
    val keywordRank: Int?,
    val vectorRank: Int?,
    val keywordNormalizedScore: Float? = null,
    val vectorNormalizedScore: Float? = null,
)

/** 검색 API 요청 */
data class SearchRequest(
    val query: String,
    val filters: SearchFilters = SearchFilters(),
    val page: Int = 0,
    val size: Int = 20,
)

/** 검색 API 응답 */
data class SearchResponse(
    val query: String,
    val totalCount: Int,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
    val results: List<SearchResultItem>,
)

/** 검색 결과 항목 */
data class SearchResultItem(
    val id: UUID,
    val insuranceCompany: CompanyInfo,
    val productId: UUID,
    val productName: String,
    val contentType: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val content: String,
    val highlight: String,
    val relevanceScore: Float,
    val dataSource: String?,
    val supplementary: SupplementaryInfo,
)

data class CompanyInfo(
    val id: UUID,
    val name: String,
    val type: String,
)

data class SupplementaryInfo(
    val hasSurrenderValues: Boolean,
    val hasPremiumExamples: Boolean,
    val hasPriceIndices: Boolean,
)
