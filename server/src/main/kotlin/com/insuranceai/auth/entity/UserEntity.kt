package com.insuranceai.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("users")
data class UserEntity(
    @Id
    @Column("id")
    @get:JvmName("userId")
    val id: UUID? = null,
    val provider: String,
    val providerId: String,
    val nickname: String,
    val email: String? = null,
    val lastLoginAt: Instant = Instant.now(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
