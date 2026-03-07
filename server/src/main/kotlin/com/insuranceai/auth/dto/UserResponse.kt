package com.insuranceai.auth.dto

import com.insuranceai.auth.entity.UserEntity
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val nickname: String,
    val email: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(entity: UserEntity): UserResponse =
            UserResponse(
                id = entity.id!!,
                nickname = entity.nickname,
                email = entity.email,
                createdAt = entity.createdAt,
            )
    }
}
