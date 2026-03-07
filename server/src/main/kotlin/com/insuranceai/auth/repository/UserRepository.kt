package com.insuranceai.auth.repository

import com.insuranceai.auth.entity.UserEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface UserRepository : CoroutineCrudRepository<UserEntity, UUID> {
    suspend fun findByProviderAndProviderId(
        provider: String,
        providerId: String,
    ): UserEntity?
}
