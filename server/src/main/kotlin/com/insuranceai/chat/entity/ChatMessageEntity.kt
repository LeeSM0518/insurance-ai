package com.insuranceai.chat.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("chat_messages")
data class ChatMessageEntity(
    @Id
    @Column("id")
    @get:JvmName("chatMessageId")
    val id: UUID? = null,
    val chatSessionId: UUID,
    val role: String,
    val content: String,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
