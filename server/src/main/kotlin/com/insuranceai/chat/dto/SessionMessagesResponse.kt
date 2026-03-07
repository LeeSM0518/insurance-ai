package com.insuranceai.chat.dto

import java.util.UUID

data class SessionMessagesResponse(
    val sessionId: UUID,
    val messages: List<ChatMessageResponse>,
)
