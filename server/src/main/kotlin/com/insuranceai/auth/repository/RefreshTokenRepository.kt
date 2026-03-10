package com.insuranceai.auth.repository

import com.insuranceai.auth.entity.RefreshTokenEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface RefreshTokenRepository : CoroutineCrudRepository<RefreshTokenEntity, UUID> {
    suspend fun findByToken(token: String): RefreshTokenEntity?

    suspend fun deleteByToken(token: String)

    suspend fun deleteByUserId(userId: UUID)

    suspend fun findByFamilyIdAndRevokedFalse(familyId: UUID): RefreshTokenEntity?

    suspend fun findAllByFamilyId(familyId: UUID): List<RefreshTokenEntity>
}
