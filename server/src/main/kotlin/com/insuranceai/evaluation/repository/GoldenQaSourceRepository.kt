package com.insuranceai.evaluation.repository

import com.insuranceai.evaluation.entity.GoldenQaSourceEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface GoldenQaSourceRepository : CoroutineCrudRepository<GoldenQaSourceEntity, UUID> {
    fun findByQaPairId(qaPairId: UUID): Flow<GoldenQaSourceEntity>

    suspend fun deleteByQaPairId(qaPairId: UUID): Long
}
