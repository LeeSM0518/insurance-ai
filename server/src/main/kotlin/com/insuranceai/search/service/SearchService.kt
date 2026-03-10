package com.insuranceai.search.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.common.util.TextUtil
import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import com.insuranceai.search.dto.CompanyInfo
import com.insuranceai.search.dto.FusedSearchResult
import com.insuranceai.search.dto.SearchFilters
import com.insuranceai.search.dto.SearchRequest
import com.insuranceai.search.dto.SearchResponse
import com.insuranceai.search.dto.SearchResultItem
import com.insuranceai.search.dto.SupplementaryInfo
import com.insuranceai.search.error.SearchError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SearchService(
    private val keywordSearchService: KeywordSearchService,
    private val vectorSearchService: VectorSearchService,
    private val rankFusionService: RankFusionService,
    private val surrenderValueRepository: SurrenderValueRepository,
    private val premiumExampleRepository: PremiumExampleRepository,
    private val productIndexRepository: ProductIndexRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun search(
        request: SearchRequest,
        userId: UUID,
        isComparison: Boolean = false,
    ): SearchResponse {
        // 쿼리 검증
        if (request.query.isBlank()) throw SearchError.QueryMissing
        if (request.query.length > 200) throw SearchError.QueryTooLong

        val fusedResults = executeHybridSearch(request.query, request.filters, isComparison)
        val totalCount = fusedResults.size

        // 페이지네이션 적용
        val offset = request.page * request.size
        val pagedResults = fusedResults.drop(offset).take(request.size)

        // 보충 데이터 존재 여부 일괄 조회
        val productIds = pagedResults.map { it.productId }.distinct()
        val supplementaryMap = loadSupplementaryExistence(productIds)

        // 하이라이트 적용
        val items =
            pagedResults.map { result ->
                val highlight =
                    if (result.keywordRank != null) {
                        result.content
                    } else {
                        TextUtil.highlightKeyword(
                            TextUtil.createSnippet(result.content, request.query),
                            request.query,
                        )
                    }

                val suppInfo =
                    supplementaryMap[result.productId] ?: SupplementaryInfo(
                        hasSurrenderValues = false,
                        hasPremiumExamples = false,
                        hasPriceIndices = false,
                    )

                SearchResultItem(
                    id = result.articleId,
                    insuranceCompany =
                        CompanyInfo(
                            id = result.companyId,
                            name = result.companyName,
                            type = result.insuranceType,
                        ),
                    productId = result.productId,
                    productName = result.productName,
                    contentType = result.contentType,
                    articleNumber = result.articleNumber,
                    articleTitle = result.articleTitle,
                    content = result.content,
                    highlight = highlight,
                    relevanceScore = result.relevanceScore,
                    dataSource = result.dataSource,
                    supplementary = suppInfo,
                )
            }

        return SearchResponse(
            query = request.query,
            totalCount = totalCount,
            page = request.page,
            size = request.size,
            hasNext = offset + request.size < totalCount,
            results = items,
        )
    }

    /** RAG용 검색 (페이지네이션 없이 Top 20 반환, 복합 질문 시 하위 질문별 검색 후 통합) */
    suspend fun searchForRag(
        query: String,
        analysis: QuestionAnalysis,
    ): List<FusedSearchResult> {
        val companyIds =
            if (analysis.companies.isNotEmpty()) {
                insuranceCompanyRepository
                    .findByNameIn(analysis.companies)
                    .map { it.id!! }
                    .toList()
                    .takeIf { it.isNotEmpty() }
            } else {
                null
            }

        val contentTypes =
            analysis.contentTypes
                .mapNotNull { name ->
                    try {
                        ContentType.valueOf(name)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }.takeIf { it.isNotEmpty() }

        val filters =
            SearchFilters(
                companyIds = companyIds,
                categories = analysis.categories.takeIf { it.isNotEmpty() },
                contentTypes = contentTypes,
            )

        // 하위 질문이 있으면 각각 검색 후 통합 (중복 제거, 점수 기준 정렬)
        val searchQueries =
            if (analysis.subQuestions.isNotEmpty()) {
                analysis.subQuestions
            } else {
                listOf(query)
            }

        val allResults =
            coroutineScope {
                searchQueries
                    .map { q ->
                        async { executeHybridSearch(q, filters, analysis.isComparison) }
                    }.awaitAll()
                    .flatten()
            }

        // articleId 기준 중복 제거 (최고 점수 유지)
        val deduplicated =
            allResults
                .groupBy { it.articleId }
                .map { (_, results) -> results.maxBy { it.relevanceScore } }
                .sortedByDescending { it.relevanceScore }

        return deduplicated.take(20)
    }

    /** 병렬 검색 + RRF 통합 공통 로직 */
    private suspend fun executeHybridSearch(
        query: String,
        filters: SearchFilters,
        isComparison: Boolean,
    ): List<FusedSearchResult> {
        val (keywordResults, vectorResults) =
            coroutineScope {
                val keywordDeferred = async { keywordSearchService.search(query, filters) }
                val vectorDeferred = async { vectorSearchService.search(query, filters) }
                keywordDeferred.await() to vectorDeferred.await()
            }

        log.debug("키워드 검색: {} 건, 벡터 검색: {} 건", keywordResults.size, vectorResults.size)

        var fusedResults = rankFusionService.fuse(keywordResults, vectorResults)

        if (isComparison) {
            fusedResults = rankFusionService.ensureDiversity(fusedResults)
        }

        return fusedResults
    }

    /** 보충 데이터 존재 여부 일괄 조회 (N+1 방지) */
    private suspend fun loadSupplementaryExistence(productIds: List<UUID>): Map<UUID, SupplementaryInfo> =
        coroutineScope {
            if (productIds.isEmpty()) return@coroutineScope emptyMap()

            val surrenderDeferred =
                async {
                    surrenderValueRepository
                        .findByProductIdIn(productIds)
                        .map { it.productId }
                        .toSet()
                }
            val premiumDeferred =
                async {
                    premiumExampleRepository
                        .findByProductIdIn(productIds)
                        .map { it.productId }
                        .toSet()
                }
            val indexDeferred =
                async {
                    productIndexRepository
                        .findByProductIdIn(productIds)
                        .map { it.productId }
                        .toSet()
                }

            val surrenderProductIds = surrenderDeferred.await()
            val premiumProductIds = premiumDeferred.await()
            val indexProductIds = indexDeferred.await()

            productIds.associateWith { productId ->
                SupplementaryInfo(
                    hasSurrenderValues = productId in surrenderProductIds,
                    hasPremiumExamples = productId in premiumProductIds,
                    hasPriceIndices = productId in indexProductIds,
                )
            }
        }
}
