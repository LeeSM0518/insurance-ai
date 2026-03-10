package com.insuranceai.search.service

import com.insuranceai.search.dto.FusedSearchResult
import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.VectorSearchResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RankFusionService {
    companion object {
        const val SINGLE_MATCH_SCALE = 0.8f // 한쪽 retriever만 매칭 시 최대 80%
    }

    /**
     * Convex Combination 기반 하이브리드 검색 결합.
     * 업계 표준 (Weaviate relativeScoreFusion, Elasticsearch linear retriever)과 동일한 방식.
     *
     * @param alpha 벡터 비중 (0=키워드만, 1=벡터만, 기본값 0.5)
     */
    fun fuse(
        keywordResults: List<KeywordSearchResult>,
        vectorResults: List<VectorSearchResult>,
        alpha: Float = 0.5f,
    ): List<FusedSearchResult> {
        if (keywordResults.isEmpty() && vectorResults.isEmpty()) return emptyList()

        // 각 retriever 점수를 개별 Min-Max 정규화
        val vectorNorm = MinMaxNormalizer(vectorResults.map { it.similarity })
        val keywordNorm = MinMaxNormalizer(keywordResults.map { it.matchQuality })

        // articleId → 정규화된 점수 및 메타데이터 수집
        data class ScoreEntry(
            val keywordNormScore: Float? = null,
            val vectorNormScore: Float? = null,
            val keywordRank: Int? = null,
            val vectorRank: Int? = null,
        )

        val scoreMap = mutableMapOf<UUID, ScoreEntry>()

        // 키워드 결과 정규화
        keywordResults.forEach { result ->
            val normalizedScore = keywordNorm.normalize(result.matchQuality)
            scoreMap[result.articleId] =
                ScoreEntry(
                    keywordNormScore = normalizedScore,
                    keywordRank = result.rank,
                )
        }

        // 벡터 결과 정규화 (similarity 내림차순 정렬 상태)
        vectorResults.forEachIndexed { index, result ->
            val normalizedScore = vectorNorm.normalize(result.similarity)
            val existing = scoreMap[result.articleId]
            scoreMap[result.articleId] =
                ScoreEntry(
                    keywordNormScore = existing?.keywordNormScore,
                    vectorNormScore = normalizedScore,
                    keywordRank = existing?.keywordRank,
                    vectorRank = index + 1,
                )
        }

        // 메타데이터 lookup 구성
        val keywordMap = keywordResults.associateBy { it.articleId }
        val vectorMap = vectorResults.associateBy { it.articleId }

        return scoreMap
            .map { (articleId, entry) ->
                val finalScore =
                    when {
                        // 양쪽 매칭: 기존 Convex Combination
                        entry.keywordNormScore != null && entry.vectorNormScore != null ->
                            alpha * entry.vectorNormScore + (1f - alpha) * entry.keywordNormScore

                        // 한쪽만 매칭: SINGLE_MATCH_SCALE 적용 (alpha 값과 무관)
                        entry.keywordNormScore != null ->
                            SINGLE_MATCH_SCALE * entry.keywordNormScore

                        entry.vectorNormScore != null ->
                            SINGLE_MATCH_SCALE * entry.vectorNormScore

                        else -> 0f
                    }

                val keyword = keywordMap[articleId]
                val vector = vectorMap[articleId]

                FusedSearchResult(
                    articleId = articleId,
                    productId = keyword?.productId ?: vector!!.productId,
                    productName = keyword?.productName ?: vector!!.productName,
                    companyId = keyword?.companyId ?: vector!!.companyId,
                    companyName = keyword?.companyName ?: vector!!.companyName,
                    insuranceType = keyword?.insuranceType ?: vector!!.insuranceType,
                    contentType = keyword?.contentType ?: vector!!.contentType,
                    articleNumber = keyword?.articleNumber ?: vector?.articleNumber,
                    articleTitle = keyword?.articleTitle ?: vector?.articleTitle,
                    content = keyword?.content ?: vector!!.content,
                    dataSource = keyword?.dataSource ?: vector?.dataSource,
                    relevanceScore = finalScore,
                    keywordRank = entry.keywordRank,
                    vectorRank = entry.vectorRank,
                    keywordNormalizedScore = entry.keywordNormScore,
                    vectorNormalizedScore = entry.vectorNormScore,
                )
            }.sortedByDescending { it.relevanceScore }
    }

    /** 다양성 보장: 보험사별 최대 maxPerCompany개 */
    fun ensureDiversity(
        results: List<FusedSearchResult>,
        maxPerCompany: Int = 3,
    ): List<FusedSearchResult> =
        results
            .groupBy { it.companyId }
            .flatMap { (_, group) -> group.take(maxPerCompany) }
            .sortedByDescending { it.relevanceScore }
}

/** 결과셋 내 Min-Max 정규화 유틸리티 */
class MinMaxNormalizer(
    scores: List<Float>,
) {
    private val min: Float = scores.minOrNull() ?: 0f
    private val max: Float = scores.maxOrNull() ?: 0f
    private val range: Float = max - min

    fun normalize(value: Float): Float = if (range > 0f) (value - min) / range else 1f
}
