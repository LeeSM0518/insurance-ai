package com.insuranceai.report.repository

import com.insuranceai.report.entity.ReportEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ReportRepository : CoroutineCrudRepository<ReportEntity, UUID> {
    fun findByUserIdAndReportTypeOrderByCreatedAtDesc(
        userId: UUID,
        reportType: String,
    ): Flow<ReportEntity>

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): Flow<ReportEntity>

    suspend fun findByShareToken(shareToken: String): ReportEntity?

    suspend fun deleteByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Int
}
