package com.insuranceai.report.dto

import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class FollowupReportRequest(
    val chatSessionId: UUID,
    val title: String? = null,
    val includeComparison: Boolean = true,
    val includeSources: Boolean = true,
    val agentName: String? = null,
    @field:Size(max = 20)
    val agentContact: String? = null,
)

data class ShareLinkRequest(
    val expiresInDays: Int = 7,
)

data class ShareLinkResponse(
    val shareToken: String,
    val shareUrl: String,
    val expiresAt: Instant,
)

data class SharedReportResponse(
    val reportType: String,
    val title: String?,
    val content: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
)
