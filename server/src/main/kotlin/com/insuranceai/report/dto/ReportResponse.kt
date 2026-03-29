package com.insuranceai.report.dto

import java.time.Instant
import java.util.UUID

data class ReportResponse(
    val id: UUID,
    val reportType: String,
    val title: String?,
    val content: String,
    val metadata: Map<String, Any>?,
    val shareToken: String?,
    val shareExpiresAt: Instant?,
    val chatSessionId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ReportSummaryResponse(
    val id: UUID,
    val reportType: String,
    val title: String?,
    val createdAt: Instant,
)
