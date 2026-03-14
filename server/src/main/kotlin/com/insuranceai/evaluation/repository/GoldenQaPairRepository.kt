package com.insuranceai.evaluation.repository

import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface GoldenQaPairRepository : CoroutineCrudRepository<GoldenQaPairEntity, UUID> {
    fun findByDatasetId(datasetId: UUID): Flow<GoldenQaPairEntity>

    fun findByDatasetIdAndStatus(
        datasetId: UUID,
        status: String,
    ): Flow<GoldenQaPairEntity>

    suspend fun countByDatasetId(datasetId: UUID): Long

    suspend fun countByDatasetIdAndStatus(
        datasetId: UUID,
        status: String,
    ): Long
}
