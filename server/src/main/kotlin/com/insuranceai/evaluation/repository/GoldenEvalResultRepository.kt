package com.insuranceai.evaluation.repository

import com.insuranceai.evaluation.entity.GoldenEvalResultEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface GoldenEvalResultRepository : CoroutineCrudRepository<GoldenEvalResultEntity, UUID> {
    fun findByEvalRunId(evalRunId: String): Flow<GoldenEvalResultEntity>

    fun findByDatasetId(datasetId: UUID): Flow<GoldenEvalResultEntity>

    suspend fun deleteByEvalRunId(evalRunId: String): Long
}
