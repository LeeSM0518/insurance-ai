package com.insuranceai.chat.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("chat_sessions")
data class ChatSessionEntity(
    @Id
    @Column("id")
    @get:JvmName("chatSessionId")
    val id: UUID? = null,
    val userId: UUID,
    val title: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
