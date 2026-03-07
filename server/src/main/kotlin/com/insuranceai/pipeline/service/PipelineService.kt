package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.entity.CrawlHistoryEntity
import com.insuranceai.pipeline.repository.CrawlHistoryRepository
import com.insuranceai.policy.domain.InsuranceType
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PipelineService(
    private val crawlHistoryRepository: CrawlHistoryRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 배치 시작 기록 */
    suspend fun startBatch(
        insuranceType: InsuranceType,
        batchId: String,
    ): CrawlHistoryEntity {
        logger.info("배치 시작: batchId=$batchId, type=$insuranceType")
        return crawlHistoryRepository.save(
            CrawlHistoryEntity(
                batchId = batchId,
                insuranceType = insuranceType.name,
                totalProducts = 0,
                successCount = 0,
                failureCount = 0,
            ),
        )
    }

    /** 배치 완료 기록 */
    suspend fun completeBatch(
        batchId: String,
        productCount: Int,
        successCount: Int,
        failureCount: Int,
    ) {
        val history =
            crawlHistoryRepository.findByBatchId(batchId)
                ?: error("배치 이력을 찾을 수 없습니다: $batchId")

        crawlHistoryRepository.save(
            history.copy(
                totalProducts = productCount,
                successCount = successCount,
                failureCount = failureCount,
            ),
        )
        logger.info("배치 완료: batchId=$batchId, 총=$productCount, 성공=$successCount, 실패=$failureCount")
    }

    /** 배치 실패 기록 */
    suspend fun failBatch(
        batchId: String,
        errorMessage: String,
    ) {
        val history =
            crawlHistoryRepository.findByBatchId(batchId)
                ?: error("배치 이력을 찾을 수 없습니다: $batchId")

        crawlHistoryRepository.save(
            history.copy(
                batchHeld = true,
                warningCount = history.warningCount + 1,
            ),
        )
        logger.error("배치 실패: batchId=$batchId, error=$errorMessage")
    }

    /** 최근 N개 배치 이력 조회 (L4 이상치 감지용) */
    suspend fun getRecentBatches(
        insuranceType: InsuranceType,
        limit: Int,
    ): List<CrawlHistoryEntity> =
        crawlHistoryRepository
            .findByInsuranceTypeOrderByCreatedAtDesc(insuranceType.name)
            .take(limit)
            .toList()
}
