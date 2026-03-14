package com.insuranceai.chat.dto

import java.time.Instant
import java.util.UUID

data class ChatMessageResponse(
    val id: UUID,
    val role: String,
    val content: String,
    val sources: List<MessageSourceResponse>,
    val createdAt: Instant,
)

/** 메시지 출처 응답 (chat_message_sources + policy_articles + products + insurance_companies JOIN) */
data class MessageSourceResponse(
    val articleId: UUID,
    val insuranceCompany: String,
    val productName: String,
    val contentType: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val relevanceScore: Float,
    val dataSourceId: UUID? = null,
    val dataSource: String? = null,
    val hasPdfSource: Boolean = false,
    val linkedPdfDataSourceId: UUID? = null,
)
