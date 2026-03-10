package com.insuranceai.chat.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("chat_feedbacks")
data class ChatFeedbackEntity(
    @Id
    @Column("id")
    @get:JvmName("chatFeedbackId")
    val id: UUID? = null,
    val chatMessageId: UUID,
    val userId: UUID,
    val feedbackType: String,
    val category: String? = null,
    val comment: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
