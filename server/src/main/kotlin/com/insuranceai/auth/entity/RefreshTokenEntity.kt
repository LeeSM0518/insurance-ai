package com.insuranceai.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("refresh_tokens")
data class RefreshTokenEntity(
    @Id
    @Column("id")
    @get:JvmName("refreshTokenId")
    val id: UUID? = null,
    val userId: UUID,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
