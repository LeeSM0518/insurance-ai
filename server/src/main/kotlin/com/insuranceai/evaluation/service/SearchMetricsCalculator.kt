package com.insuranceai.evaluation.service

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class SearchMetrics(
    val contextPrecision: BigDecimal,
    val contextRecall: BigDecimal,
    val mrr: BigDecimal,
    val ndcg: BigDecimal,
)

@Component
class SearchMetricsCalculator {
    /** 검색 지표 계산 */
    fun calculate(
        retrievedArticleIds: List<UUID>,
        groundTruthArticleIds: Set<UUID>,
        k: Int = 10,
    ): SearchMetrics {
        if (groundTruthArticleIds.isEmpty()) {
            return SearchMetrics(
                contextPrecision = BigDecimal.ZERO,
                contextRecall = BigDecimal.ZERO,
                mrr = BigDecimal.ZERO,
                ndcg = BigDecimal.ZERO,
            )
        }

        val topK = retrievedArticleIds.take(k)

        return SearchMetrics(
            contextPrecision = calcPrecisionAtK(topK, groundTruthArticleIds),
            contextRecall = calcRecall(retrievedArticleIds, groundTruthArticleIds),
            mrr = calcMrr(retrievedArticleIds, groundTruthArticleIds),
            ndcg = calcNdcgAtK(topK, groundTruthArticleIds),
        )
    }

    /** Context Precision@K = (검색된 K개 중 관련 문서 수) / K */
    private fun calcPrecisionAtK(
        topK: List<UUID>,
        groundTruth: Set<UUID>,
    ): BigDecimal {
        if (topK.isEmpty()) return BigDecimal.ZERO
        val relevant = topK.count { it in groundTruth }
        return BigDecimal(relevant).divide(BigDecimal(topK.size), 4, RoundingMode.HALF_UP)
    }

    /** Context Recall = (ground truth 중 검색된 수) / (전체 ground truth 수) */
    private fun calcRecall(
        retrieved: List<UUID>,
        groundTruth: Set<UUID>,
    ): BigDecimal {
        val retrievedSet = retrieved.toSet()
        val found = groundTruth.count { it in retrievedSet }
        return BigDecimal(found).divide(BigDecimal(groundTruth.size), 4, RoundingMode.HALF_UP)
    }

    /** MRR = 1 / (첫 번째 관련 문서의 순위) */
    private fun calcMrr(
        retrieved: List<UUID>,
        groundTruth: Set<UUID>,
    ): BigDecimal {
        for ((index, id) in retrieved.withIndex()) {
            if (id in groundTruth) {
                return BigDecimal.ONE.divide(BigDecimal(index + 1), 4, RoundingMode.HALF_UP)
            }
        }
        return BigDecimal.ZERO
    }

    /** NDCG@K = DCG@K / idealDCG@K (binary relevance) */
    private fun calcNdcgAtK(
        topK: List<UUID>,
        groundTruth: Set<UUID>,
    ): BigDecimal {
        val dcg = calcDcg(topK, groundTruth)
        // ideal: 관련 문서를 모두 상위에 배치
        val idealCount = minOf(groundTruth.size, topK.size)
        val idealList = (1..idealCount).map { UUID.randomUUID() } // placeholder
        val idealDcg = calcIdealDcg(idealCount)

        if (idealDcg == BigDecimal.ZERO) return BigDecimal.ZERO
        return dcg.divide(idealDcg, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
    }

    private fun calcDcg(
        topK: List<UUID>,
        groundTruth: Set<UUID>,
    ): BigDecimal {
        var dcg = BigDecimal.ZERO
        for ((index, id) in topK.withIndex()) {
            if (id in groundTruth) {
                // binary relevance: rel=1 for relevant, 0 otherwise
                // DCG = sum(1 / log2(i+2)) for i starting at 0
                dcg = dcg.add(BigDecimal(1.0 / Math.log(index + 2.0) * Math.log(2.0)))
            }
        }
        return dcg.setScale(4, RoundingMode.HALF_UP)
    }

    private fun calcIdealDcg(count: Int): BigDecimal {
        var dcg = BigDecimal.ZERO
        for (i in 0 until count) {
            dcg = dcg.add(BigDecimal(1.0 / Math.log(i + 2.0) * Math.log(2.0)))
        }
        return dcg.setScale(4, RoundingMode.HALF_UP)
    }
}
