package com.insuranceai.pipeline.repository

import com.insuranceai.pipeline.entity.CrawlHistoryEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface CrawlHistoryRepository : CoroutineCrudRepository<CrawlHistoryEntity, UUID> {
    /** 보험 유형별 최근 배치 이력 조회 (created_at DESC) */
    fun findByInsuranceTypeOrderByCreatedAtDesc(insuranceType: String): Flow<CrawlHistoryEntity>

    /** 배치 ID로 조회 */
    suspend fun findByBatchId(batchId: String): CrawlHistoryEntity?
}
