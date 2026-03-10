package com.insuranceai.chat.dto

import com.insuranceai.chat.domain.FeedbackCategory
import com.insuranceai.chat.domain.FeedbackType

data class FeedbackRequest(
    val feedbackType: FeedbackType,
    val category: FeedbackCategory? = null,
    val comment: String? = null,
)
