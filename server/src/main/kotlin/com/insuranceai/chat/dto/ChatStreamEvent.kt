package com.insuranceai.chat.dto

import java.util.UUID

/** SSE 이벤트 래퍼 (02-search-chat.md 기반) */
data class ChatStreamEvent(
    val type: String,
    val data: Any,
)

data class SearchingEvent(
    val message: String,
)

data class SourcesFoundEvent(
    val sources: List<SourceInfo>,
)

data class GeneratingEvent(
    val message: String,
)

data class TokenEvent(
    val content: String,
)

data class DoneEvent(
    val messageId: UUID,
    val suggestedQuestions: List<String>,
    val supplementary: SupplementaryData? = null,
)

/** Step 10에서 Policy DTO 구현 시 List<Any> → 구체적 DTO 타입으로 변경 예정 */
data class SupplementaryData(
    val surrenderValues: List<Any> = emptyList(),
    val premiumExamples: List<Any> = emptyList(),
    val priceIndices: List<Any> = emptyList(),
)

data class ErrorEvent(
    val code: String,
    val message: String,
)

data class SourceInfo(
    val articleId: UUID,
    val insuranceCompany: String,
    val productId: UUID,
    val productName: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val relevanceScore: Float,
    val dataSource: String,
)
