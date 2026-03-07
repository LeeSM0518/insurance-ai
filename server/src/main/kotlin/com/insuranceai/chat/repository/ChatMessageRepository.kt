package com.insuranceai.chat.repository

import com.insuranceai.chat.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ChatMessageRepository : CoroutineCrudRepository<ChatMessageEntity, UUID> {
    fun findByChatSessionIdOrderByCreatedAt(sessionId: UUID): Flow<ChatMessageEntity>
}
