package com.insuranceai.chat.repository

import com.insuranceai.chat.entity.ChatMessageSourceEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ChatMessageSourceRepository : CoroutineCrudRepository<ChatMessageSourceEntity, UUID> {
    fun findByChatMessageId(messageId: UUID): Flow<ChatMessageSourceEntity>
}
