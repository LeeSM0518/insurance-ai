package com.insuranceai.evaluation.service

import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchMetricsCalculatorTest {
    private val calculator = SearchMetricsCalculator()

    private val id1 = UUID.randomUUID()
    private val id2 = UUID.randomUUID()
    private val id3 = UUID.randomUUID()
    private val id4 = UUID.randomUUID()
    private val id5 = UUID.randomUUID()

    @Test
    fun `Precision@K - 관련 문서 2개가 상위 5개에 포함`() {
        val retrieved = listOf(id1, id2, id3, id4, id5)
        val groundTruth = setOf(id1, id3)

        val metrics = calculator.calculate(retrieved, groundTruth, k = 5)

        // 2/5 = 0.4
        assertEquals(BigDecimal("0.4000"), metrics.contextPrecision)
    }

    @Test
    fun `Recall - 관련 문서 3개 중 2개 검색`() {
        val retrieved = listOf(id1, id2, id4)
        val groundTruth = setOf(id1, id3, id4)

        val metrics = calculator.calculate(retrieved, groundTruth)

        // 2/3 = 0.6667
        assertEquals(BigDecimal("0.6667"), metrics.contextRecall)
    }

    @Test
    fun `MRR - 첫 번째 관련 문서가 3번째`() {
        val retrieved = listOf(id2, id4, id1, id5)
        val groundTruth = setOf(id1, id3)

        val metrics = calculator.calculate(retrieved, groundTruth)

        // 1/3 = 0.3333
        assertEquals(BigDecimal("0.3333"), metrics.mrr)
    }

    @Test
    fun `MRR - 첫 번째 관련 문서가 1번째`() {
        val retrieved = listOf(id1, id2, id3)
        val groundTruth = setOf(id1)

        val metrics = calculator.calculate(retrieved, groundTruth)

        assertEquals(BigDecimal("1.0000"), metrics.mrr)
    }

    @Test
    fun `ground truth가 비어있으면 모두 0`() {
        val retrieved = listOf(id1, id2)
        val metrics = calculator.calculate(retrieved, emptySet())

        assertEquals(BigDecimal.ZERO, metrics.contextPrecision)
        assertEquals(BigDecimal.ZERO, metrics.contextRecall)
        assertEquals(BigDecimal.ZERO, metrics.mrr)
        assertEquals(BigDecimal.ZERO, metrics.ndcg)
    }

    @Test
    fun `NDCG - 완벽한 순서이면 1점`() {
        val retrieved = listOf(id1, id2, id3)
        val groundTruth = setOf(id1, id2, id3)

        val metrics = calculator.calculate(retrieved, groundTruth, k = 3)

        assertEquals(BigDecimal("1.0000"), metrics.ndcg)
    }

    @Test
    fun `NDCG - 관련 문서가 뒤에 있으면 낮은 점수`() {
        val retrieved = listOf(id4, id5, id1)
        val groundTruth = setOf(id1)

        val metrics = calculator.calculate(retrieved, groundTruth, k = 3)

        assertTrue(metrics.ndcg < BigDecimal.ONE)
        assertTrue(metrics.ndcg > BigDecimal.ZERO)
    }
}
