package com.insuranceai.chat.dto

import com.insuranceai.chat.domain.FeedbackCategory
import com.insuranceai.chat.domain.FeedbackType
import java.time.Instant
import java.util.UUID

data class FeedbackResponse(
    val id: UUID,
    val chatMessageId: UUID,
    val feedbackType: FeedbackType,
    val category: FeedbackCategory?,
    val comment: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
