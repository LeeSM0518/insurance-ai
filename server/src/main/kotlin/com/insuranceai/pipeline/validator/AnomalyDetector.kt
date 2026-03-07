package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.repository.CrawlHistoryRepository
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.math.sqrt

/**
 * L4 검증: 이상치 감지 (배치별 상품 수 급변)
 * - 이력 < 5회: 이전 대비 30% 이상 감소 시 배치 보류
 * - 이력 >= 5회: Z-Score 기반 이상치 감지
 */
@Component
class AnomalyDetector(
    private val crawlHistoryRepository: CrawlHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun detect(
        insuranceType: String,
        currentCount: Int,
    ): AnomalyResult {
        val history =
            crawlHistoryRepository
                .findByInsuranceTypeOrderByCreatedAtDesc(insuranceType)
                .take(HISTORY_WINDOW)
                .toList()

        // 이력이 없으면 정상
        if (history.isEmpty()) {
            return AnomalyResult(isAnomaly = false, batchHeld = false)
        }

        val previousCounts = history.map { it.totalProducts }

        return if (history.size < STABLE_THRESHOLD) {
            // 초기 단계: 단순 비율 비교
            detectEarlyStage(currentCount, previousCounts)
        } else {
            // 안정화 단계: Z-Score 기반
            detectStableStage(currentCount, previousCounts)
        }
    }

    /** 초기 단계 (이력 < 5회): 이전 대비 30% 이상 감소 시 배치 보류 */
    internal fun detectEarlyStage(
        currentCount: Int,
        previousCounts: List<Int>,
    ): AnomalyResult {
        val lastCount = previousCounts.first()
        if (lastCount == 0) return AnomalyResult(isAnomaly = false, batchHeld = false)

        val changeRate = (currentCount - lastCount).toDouble() / lastCount

        return if (changeRate < -EARLY_DROP_THRESHOLD) {
            log.warn("L4 이상치 감지 (초기): 상품 수 {} → {} ({}% 감소)", lastCount, currentCount, (changeRate * 100).toInt())
            AnomalyResult(
                isAnomaly = true,
                batchHeld = true,
                reason = "상품 수 ${(changeRate * 100).toInt()}% 감소 ($lastCount → $currentCount)",
            )
        } else {
            AnomalyResult(isAnomaly = false, batchHeld = false)
        }
    }

    /** 안정화 단계 (이력 >= 5회): Z-Score 기반 이상치 감지 */
    internal fun detectStableStage(
        currentCount: Int,
        previousCounts: List<Int>,
    ): AnomalyResult {
        val zScore = calculateZScore(currentCount, previousCounts)

        return when {
            zScore < CRITICAL_Z_SCORE -> {
                log.warn("L4 이상치 감지 (안정화): Z-Score={}, 상품 수 {}", zScore, currentCount)
                AnomalyResult(
                    isAnomaly = true,
                    batchHeld = true,
                    reason = "Z-Score ${"%.2f".format(zScore)} < $CRITICAL_Z_SCORE (상품 수 $currentCount)",
                )
            }
            zScore < WARNING_Z_SCORE -> {
                log.warn("L4 경고: Z-Score={}, 상품 수 {}", zScore, currentCount)
                AnomalyResult(
                    isAnomaly = true,
                    batchHeld = false,
                    reason = "Z-Score ${"%.2f".format(zScore)} 경고 수준 (상품 수 $currentCount)",
                )
            }
            else -> {
                AnomalyResult(isAnomaly = false, batchHeld = false)
            }
        }
    }

    /** Z-Score 계산 */
    internal fun calculateZScore(
        value: Int,
        samples: List<Int>,
    ): Double {
        if (samples.isEmpty()) return 0.0
        val mean = samples.average()
        val stdDev = calculateStdDev(samples, mean)
        if (stdDev == 0.0) return 0.0
        return (value - mean) / stdDev
    }

    /** 표준편차 계산 */
    private fun calculateStdDev(
        samples: List<Int>,
        mean: Double,
    ): Double {
        if (samples.size < 2) return 0.0
        val variance = samples.sumOf { (it - mean) * (it - mean) } / (samples.size - 1)
        return sqrt(variance)
    }

    companion object {
        const val HISTORY_WINDOW = 10
        const val STABLE_THRESHOLD = 5
        const val EARLY_DROP_THRESHOLD = 0.3
        const val CRITICAL_Z_SCORE = -3.0
        const val WARNING_Z_SCORE = -2.0
    }
}

/** 이상치 감지 결과 */
data class AnomalyResult(
    val isAnomaly: Boolean,
    val batchHeld: Boolean,
    val reason: String? = null,
)
