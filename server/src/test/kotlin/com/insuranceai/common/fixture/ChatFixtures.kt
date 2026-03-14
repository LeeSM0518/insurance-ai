package com.insuranceai.common.fixture

import com.insuranceai.chat.domain.MessageRole
import com.insuranceai.chat.entity.ChatFeedbackEntity
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.chat.entity.ChatMessageSourceEntity
import com.insuranceai.chat.entity.ChatSessionEntity
import java.util.UUID

/**
 * 채팅 관련 테스트 데이터 빌더.
 */
object ChatFixtures {
    fun sessionEntity(
        userId: UUID,
        title: String? = null,
    ) = ChatSessionEntity(
        userId = userId,
        title = title,
    )

    fun userMessageEntity(
        chatSessionId: UUID,
        content: String = "암보험 면책기간이 뭔가요?",
    ) = ChatMessageEntity(
        chatSessionId = chatSessionId,
        role = MessageRole.USER.name,
        content = content,
    )

    fun assistantMessageEntity(
        chatSessionId: UUID,
        content: String = "면책기간은 보험 계약 후 보장이 시작되지 않는 기간입니다.",
    ) = ChatMessageEntity(
        chatSessionId = chatSessionId,
        role = MessageRole.ASSISTANT.name,
        content = content,
    )

    fun messageSourceEntity(
        chatMessageId: UUID,
        policyArticleId: UUID,
        relevanceScore: Float = 0.85f,
    ) = ChatMessageSourceEntity(
        chatMessageId = chatMessageId,
        policyArticleId = policyArticleId,
        relevanceScore = relevanceScore,
    )

    fun feedbackEntity(
        chatMessageId: UUID,
        userId: UUID,
        feedbackType: String = "POSITIVE",
        category: String? = null,
        comment: String? = null,
    ) = ChatFeedbackEntity(
        chatMessageId = chatMessageId,
        userId = userId,
        feedbackType = feedbackType,
        category = category,
        comment = comment,
    )
}
