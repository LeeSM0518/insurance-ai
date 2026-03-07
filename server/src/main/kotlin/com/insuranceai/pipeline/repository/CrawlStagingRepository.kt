package com.insuranceai.pipeline.repository

import com.insuranceai.pipeline.entity.CrawlStagingEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface CrawlStagingRepository : CoroutineCrudRepository<CrawlStagingEntity, UUID> {
    fun findByBatchId(batchId: String): Flow<CrawlStagingEntity>

    fun findByValidationStatus(status: String): Flow<CrawlStagingEntity>
}
