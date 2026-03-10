package com.insuranceai.chat.repository

import com.insuranceai.chat.entity.ChatFeedbackEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ChatFeedbackRepository : CoroutineCrudRepository<ChatFeedbackEntity, UUID> {
    suspend fun findByChatMessageIdAndUserId(
        chatMessageId: UUID,
        userId: UUID,
    ): ChatFeedbackEntity?

    suspend fun deleteByChatMessageIdAndUserId(
        chatMessageId: UUID,
        userId: UUID,
    ): Long
}
