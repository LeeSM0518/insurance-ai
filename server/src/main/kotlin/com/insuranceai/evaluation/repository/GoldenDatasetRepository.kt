package com.insuranceai.evaluation.repository

import com.insuranceai.evaluation.entity.GoldenDatasetEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface GoldenDatasetRepository : CoroutineCrudRepository<GoldenDatasetEntity, UUID> {
    suspend fun findByNameAndVersion(
        name: String,
        version: String,
    ): GoldenDatasetEntity?

    fun findByStatus(status: String): Flow<GoldenDatasetEntity>
}
