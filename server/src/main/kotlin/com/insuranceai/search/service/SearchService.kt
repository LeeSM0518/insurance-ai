package com.insuranceai.search.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.common.util.TextUtil
import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
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
    private val policyArticleRepository: PolicyArticleRepository,
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
                subcategories = analysis.subcategories.takeIf { it.isNotEmpty() },
                contentTypes = contentTypes,
            )

        val searchKeywords = analysis.searchKeywords.takeIf { it.isNotEmpty() }

        // 하위 질문이 있으면 각각 검색 후 통합 (중복 제거, 점수 기준 정렬)
        // 원본 쿼리도 함께 검색하여 결과 확보
        val searchQueries =
            if (analysis.subQuestions.isNotEmpty()) {
                analysis.subQuestions + query
            } else {
                listOf(query)
            }

        val allResults =
            coroutineScope {
                searchQueries
                    .map { q ->
                        async {
                            executeHybridSearchWithFallback(
                                q,
                                filters,
                                analysis.isComparison,
                                searchKeywords,
                            )
                        }
                    }.awaitAll()
                    .flatten()
            }

        // articleId 기준 중복 제거 (최고 점수 유지)
        val deduplicated =
            allResults
                .groupBy { it.articleId }
                .map { (_, results) -> results.maxBy { it.relevanceScore } }
                .sortedByDescending { it.relevanceScore }

        val top20 = deduplicated.take(20)
        return enrichWithExemptions(top20, analysis.searchKeywords)
    }

    /** 단계적 필터 완화를 통한 폴백 검색 (RAG 전용) */
    private suspend fun executeHybridSearchWithFallback(
        query: String,
        filters: SearchFilters,
        isComparison: Boolean,
        searchKeywords: List<String>? = null,
    ): List<FusedSearchResult> {
        log.info("RAG 검색 시작: query='{}', filters={}", query, filters)

        // 1차: 원본 필터
        val results = executeHybridSearch(query, filters, isComparison, searchKeywords)
        val keywordCoverage = calculateKeywordCoverage(searchKeywords, results)
        val hasGoodCoverage = keywordCoverage >= KEYWORD_COVERAGE_THRESHOLD
        val topScore = results.firstOrNull()?.relevanceScore ?: 0f
        // 키워드가 있으면 커버리지 기반 판단, 없으면 topScore 기반 판단
        val hasKeywords = !searchKeywords.isNullOrEmpty()
        val isFirstSearchSufficient =
            if (hasKeywords) hasGoodCoverage else (results.any { it.keywordRank != null } || topScore >= 0.5f)
        if (results.isNotEmpty() && isFirstSearchSufficient) return results

        if (results.isNotEmpty()) {
            log.info("검색 품질 부족으로 폴백 진행: keywordCoverage={}, topScore={}", keywordCoverage, topScore)
        }

        // 1.5차: 커버리지 부족 시 subcategories 먼저 완화
        if (!hasGoodCoverage && results.isNotEmpty() && filters.subcategories != null) {
            log.info("키워드 커버리지 부족 (coverage={}): subcategories 필터 완화", keywordCoverage)
            val relaxedSub = filters.copy(subcategories = null)
            val relaxedResults = executeHybridSearch(query, relaxedSub, isComparison, searchKeywords)
            val newCoverage = calculateKeywordCoverage(searchKeywords, relaxedResults)
            if (newCoverage > keywordCoverage && relaxedResults.isNotEmpty()) {
                return relaxedResults
            }
        }

        // 2차: contentTypes 제거
        val relaxed1 = filters.copy(contentTypes = null)
        val results1 = executeHybridSearch(query, relaxed1, isComparison, searchKeywords)
        if (results1.isNotEmpty()) {
            log.info("검색 폴백: contentTypes 필터 제거 후 {} 건 발견", results1.size)
            return results1
        }

        // 3차: subcategories도 제거
        val relaxed2 = relaxed1.copy(subcategories = null)
        val results2 = executeHybridSearch(query, relaxed2, isComparison, searchKeywords)
        if (results2.isNotEmpty()) {
            log.info("검색 폴백: subcategories 필터 제거 후 {} 건 발견", results2.size)
            return results2
        }

        // 4차: categories 제거 (companyIds는 유지하여 보험사 맥락 보존)
        val relaxed3 = relaxed2.copy(categories = null)
        val results3 = executeHybridSearch(query, relaxed3, isComparison, searchKeywords)
        if (results3.isNotEmpty()) {
            log.info("검색 폴백: categories 필터 제거 후 {} 건 발견", results3.size)
            return results3
        }

        // 5차: companyIds까지 제거 (전체 검색)
        val noFilters = SearchFilters()
        val results4 = executeHybridSearch(query, noFilters, isComparison, searchKeywords)
        log.info("검색 폴백: 전체 검색 {} 건 발견", results4.size)
        return results4
    }

    /** 검색 결과에서 각 키워드가 얼마나 커버되는지 비율 계산 */
    private fun calculateKeywordCoverage(
        searchKeywords: List<String>?,
        results: List<FusedSearchResult>,
    ): Float {
        if (searchKeywords.isNullOrEmpty()) return 1.0f
        val topResults = results.take(10)
        val coveredKeywords =
            searchKeywords.count { keyword ->
                topResults.any { result ->
                    result.articleTitle?.contains(keyword) == true || result.content.contains(keyword)
                }
            }
        return coveredKeywords.toFloat() / searchKeywords.size
    }

    /**
     * 검색된 보장내용(COVERAGE/SPECIAL_TERMS) 조문과 동일 상품의 면책/제외(EXEMPTION) 조문을 보강
     * - Top 결과에서 COVERAGE/SPECIAL_TERMS 타입 조문의 productId를 수집
     * - 동일 productId의 EXEMPTION 조문 중, 질문 키워드를 포함하는 조문을 추가
     * - 기존 결과 뒤에 면책 조문을 최대 3개 추가
     */
    private suspend fun enrichWithExemptions(
        results: List<FusedSearchResult>,
        searchKeywords: List<String>,
    ): List<FusedSearchResult> {
        if (searchKeywords.isEmpty()) return results

        // COVERAGE 또는 SPECIAL_TERMS 조문의 productId 수집
        val coverageProductIds =
            results
                .filter { it.contentType == ContentType.COVERAGE.name || it.contentType == ContentType.SPECIAL_TERMS.name }
                .map { it.productId }
                .distinct()

        if (coverageProductIds.isEmpty()) return results

        val existingArticleIds = results.map { it.articleId }.toSet()

        // 해당 상품들의 EXEMPTION 조문 조회
        val exemptionArticles =
            policyArticleRepository
                .findByProductIdInAndContentTypeAndIsActive(
                    productIds = coverageProductIds,
                    contentType = ContentType.EXEMPTION.name,
                    isActive = true,
                ).toList()

        // 키워드 포함 여부로 필터링 + 기존 결과와 중복 제거
        val matchingExemptions =
            exemptionArticles
                .filter { article ->
                    article.id !in existingArticleIds &&
                        searchKeywords.any { keyword ->
                            article.content.contains(keyword, ignoreCase = true) ||
                                article.articleTitle?.contains(keyword, ignoreCase = true) == true
                        }
                }.take(MAX_EXEMPTION_ENRICHMENT)

        if (matchingExemptions.isEmpty()) return results

        // 기존 결과에서 동일 productId의 보험사 정보 복사
        val productInfoMap =
            results
                .associateBy { it.productId }

        val enrichedResults =
            matchingExemptions.mapNotNull { article ->
                val refResult = productInfoMap[article.productId] ?: return@mapNotNull null
                FusedSearchResult(
                    articleId = article.id!!,
                    productId = article.productId,
                    productName = refResult.productName,
                    companyId = refResult.companyId,
                    companyName = refResult.companyName,
                    insuranceType = refResult.insuranceType,
                    contentType = article.contentType,
                    articleNumber = article.articleNumber,
                    articleTitle = article.articleTitle,
                    content = article.content,
                    dataSource = refResult.dataSource,
                    dataSourceId = article.dataSourceId,
                    relevanceScore = 0f,
                    keywordRank = null,
                    vectorRank = null,
                )
            }

        if (enrichedResults.isNotEmpty()) {
            log.info("면책 조문 보강: {}개 추가 (productIds={})", enrichedResults.size, coverageProductIds)
        }

        return results + enrichedResults
    }

    companion object {
        const val KEYWORD_COVERAGE_THRESHOLD = 0.5f
        const val MAX_EXEMPTION_ENRICHMENT = 3
    }

    /** 병렬 검색 + RRF 통합 공통 로직 */
    private suspend fun executeHybridSearch(
        query: String,
        filters: SearchFilters,
        isComparison: Boolean,
        searchKeywords: List<String>? = null,
    ): List<FusedSearchResult> {
        val (keywordResults, vectorResults) =
            coroutineScope {
                val keywordDeferred =
                    async {
                        if (searchKeywords != null) {
                            keywordSearchService.searchByKeywords(searchKeywords, filters)
                        } else {
                            keywordSearchService.search(query, filters)
                        }
                    }
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
