package com.insuranceai.chat.dto

import java.time.Instant
import java.util.UUID

data class ChatSessionResponse(
    val id: UUID,
    val title: String?,
    val lastMessagePreview: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
