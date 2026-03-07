package com.insuranceai.search.service

import com.insuranceai.search.dto.FusedSearchResult
import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.VectorSearchResult
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RankFusionService {
    /** RRF 통합 (k=60) */
    fun fuse(
        keywordResults: List<KeywordSearchResult>,
        vectorResults: List<VectorSearchResult>,
        k: Int = 60,
    ): List<FusedSearchResult> {
        // articleId → (키워드 RRF 점수, 벡터 RRF 점수, 키워드 rank, 벡터 rank)
        data class ScoreEntry(
            val keywordScore: Float = 0f,
            val vectorScore: Float = 0f,
            val keywordRank: Int? = null,
            val vectorRank: Int? = null,
        )

        val scoreMap = mutableMapOf<UUID, ScoreEntry>()

        // 키워드 결과 RRF 점수 계산
        keywordResults.forEach { result ->
            val rrfScore = 1f / (k + result.rank)
            scoreMap[result.articleId] =
                ScoreEntry(
                    keywordScore = rrfScore,
                    keywordRank = result.rank,
                )
        }

        // 벡터 결과 RRF 점수 계산 (similarity 내림차순으로 이미 정렬된 상태)
        vectorResults.forEachIndexed { index, result ->
            val vectorRank = index + 1
            val rrfScore = 1f / (k + vectorRank)
            val existing = scoreMap[result.articleId]
            scoreMap[result.articleId] =
                ScoreEntry(
                    keywordScore = existing?.keywordScore ?: 0f,
                    vectorScore = rrfScore,
                    keywordRank = existing?.keywordRank,
                    vectorRank = vectorRank,
                )
        }

        // 통합 점수 계산
        val rawScores = scoreMap.mapValues { (_, entry) -> entry.keywordScore + entry.vectorScore }

        // min-max 정규화
        val minScore = rawScores.values.minOrNull() ?: 0f
        val maxScore = rawScores.values.maxOrNull() ?: 0f
        val scoreRange = maxScore - minScore

        // 모든 결과 매핑을 위한 lookup 구성
        val keywordMap = keywordResults.associateBy { it.articleId }
        val vectorMap = vectorResults.associateBy { it.articleId }

        return scoreMap
            .map { (articleId, entry) ->
                val rawScore = rawScores[articleId]!!
                val normalizedScore =
                    if (scoreRange > 0f) (rawScore - minScore) / scoreRange else 1f

                // 키워드 결과 또는 벡터 결과에서 메타데이터 추출
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
                    relevanceScore = normalizedScore,
                    keywordRank = entry.keywordRank,
                    vectorRank = entry.vectorRank,
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
