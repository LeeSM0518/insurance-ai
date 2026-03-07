package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.entity.CrawlHistoryEntity
import com.insuranceai.pipeline.repository.CrawlHistoryRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnomalyDetectorTest {
    private val crawlHistoryRepository = mockk<CrawlHistoryRepository>()
    private val detector = AnomalyDetector(crawlHistoryRepository)

    private fun historyEntity(totalProducts: Int) =
        CrawlHistoryEntity(
            batchId = "batch-test",
            insuranceType = "LIFE",
            totalProducts = totalProducts,
            successCount = totalProducts,
            failureCount = 0,
        )

    @Test
    fun `이력이 없으면 정상으로 판정한다`() =
        runTest {
            every {
                crawlHistoryRepository.findByInsuranceTypeOrderByCreatedAtDesc("LIFE")
            } returns flowOf()

            val result = detector.detect("LIFE", 100)
            assertFalse(result.isAnomaly)
            assertFalse(result.batchHeld)
        }

    @Test
    fun `초기 단계에서 30% 이상 감소하면 배치 보류`() {
        val result = detector.detectEarlyStage(60, listOf(100))
        assertTrue(result.isAnomaly)
        assertTrue(result.batchHeld)
    }

    @Test
    fun `초기 단계에서 30% 미만 감소는 정상`() {
        val result = detector.detectEarlyStage(80, listOf(100))
        assertFalse(result.isAnomaly)
        assertFalse(result.batchHeld)
    }

    @Test
    fun `초기 단계에서 증가는 정상`() {
        val result = detector.detectEarlyStage(120, listOf(100))
        assertFalse(result.isAnomaly)
    }

    @Test
    fun `초기 단계에서 이전 값이 0이면 정상`() {
        val result = detector.detectEarlyStage(100, listOf(0))
        assertFalse(result.isAnomaly)
    }

    @Test
    fun `안정화 단계에서 Z-Score가 -3 미만이면 배치 보류`() {
        // 평균 100, 표준편차 약 3.16인 샘플에서 85는 z-score가 매우 낮음
        val previousCounts = listOf(100, 102, 98, 101, 99, 103, 97, 100, 101, 99)
        val result = detector.detectStableStage(85, previousCounts)
        assertTrue(result.batchHeld)
    }

    @Test
    fun `안정화 단계에서 Z-Score가 -2 ~ -3이면 경고만`() {
        // 평균=100, 표준편차≈1.83 → Z-Score(-2~-3) 범위: 약 94.5~96.3
        val previousCounts = listOf(100, 102, 98, 101, 99, 103, 97, 100, 101, 99)
        val result = detector.detectStableStage(96, previousCounts)
        assertTrue(result.isAnomaly)
        assertFalse(result.batchHeld)
    }

    @Test
    fun `안정화 단계에서 정상 범위면 이상치 아님`() {
        val previousCounts = listOf(100, 102, 98, 101, 99)
        val result = detector.detectStableStage(99, previousCounts)
        assertFalse(result.isAnomaly)
    }

    @Test
    fun `Z-Score 계산이 정확하다`() {
        // 평균 = 10, 표준편차 ≈ 1
        val samples = listOf(9, 10, 11)
        val zScore = detector.calculateZScore(10, samples)
        assertEquals(0.0, zScore, 0.01)
    }

    @Test
    fun `표준편차가 0이면 Z-Score는 0을 반환`() {
        val samples = listOf(100, 100, 100)
        val zScore = detector.calculateZScore(50, samples)
        assertEquals(0.0, zScore)
    }
}
