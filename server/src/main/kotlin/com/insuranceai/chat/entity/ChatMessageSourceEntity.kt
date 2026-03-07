package com.insuranceai.chat.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

@Table("chat_message_sources")
data class ChatMessageSourceEntity(
    @Id
    @Column("id")
    @get:JvmName("chatMessageSourceId")
    val id: UUID? = null,
    val chatMessageId: UUID,
    val policyArticleId: UUID,
    val relevanceScore: Float,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
