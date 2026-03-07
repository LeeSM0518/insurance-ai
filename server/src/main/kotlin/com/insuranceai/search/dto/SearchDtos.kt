package com.insuranceai.search.dto

import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.InsuranceType
import java.util.UUID

/** 검색 필터 */
data class SearchFilters(
    val companyIds: List<UUID>? = null,
    val insuranceType: InsuranceType? = null,
    val categories: List<String>? = null,
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
    val rank: Int,
)
