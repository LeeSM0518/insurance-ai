package com.insuranceai.chat.repository

import com.insuranceai.chat.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ChatSessionRepository : CoroutineCrudRepository<ChatSessionEntity, UUID> {
    fun findByUserIdOrderByUpdatedAtDesc(userId: UUID): Flow<ChatSessionEntity>

    suspend fun deleteByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Int
}
