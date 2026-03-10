package com.insuranceai.auth.service

import com.insuranceai.auth.entity.RefreshTokenEntity
import com.insuranceai.auth.property.JwtProperties
import com.insuranceai.auth.repository.RefreshTokenRepository
import io.jsonwebtoken.Jwts
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class TokenService(
    private val jwtProperties: JwtProperties,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val dsl: DSLContext,
) {
    fun createAccessToken(userId: UUID): String {
        val now = Instant.now()
        val expiration = jwtProperties.addAccessTokenExpiration(now)
        return Jwts
            .builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(jwtProperties.accessTokenSecretKey)
            .compact()
    }

    suspend fun createRefreshToken(
        userId: UUID,
        familyId: UUID? = null,
    ): String {
        val now = Instant.now()
        val expiration = jwtProperties.addRefreshTokenExpiration(now)
        val token =
            Jwts
                .builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(jwtProperties.refreshTokenSecretKey)
                .compact()

        val resolvedFamilyId = familyId ?: UUID.randomUUID()

        // 같은 family의 활성 토큰을 찾아 revoked 마킹
        if (familyId != null) {
            val existing = refreshTokenRepository.findByFamilyIdAndRevokedFalse(familyId)
            if (existing != null) {
                refreshTokenRepository.save(
                    existing.copy(revoked = true, updatedAt = Instant.now()),
                )
            }
        } else {
            // 신규 로그인: 기존 사용자의 모든 토큰 삭제 후 새 family 시작
            refreshTokenRepository.deleteByUserId(userId)
        }

        val saved =
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = userId,
                    token = token,
                    familyId = resolvedFamilyId,
                    expiresAt = expiration,
                ),
            )

        // 이전 토큰의 replacedBy를 새 토큰 ID로 업데이트
        if (familyId != null) {
            val previous =
                refreshTokenRepository
                    .findAllByFamilyId(resolvedFamilyId)
                    .firstOrNull { it.id != saved.id && it.replacedBy == null && it.revoked }
            if (previous != null) {
                refreshTokenRepository.save(
                    previous.copy(replacedBy = saved.id, updatedAt = Instant.now()),
                )
            }
        }

        return token
    }

    fun validateAccessToken(token: String): Boolean =
        runCatching {
            Jwts
                .parser()
                .verifyWith(jwtProperties.accessTokenSecretKey)
                .build()
                .parseSignedClaims(token)
        }.isSuccess

    /**
     * RT의 JWT 서명만 검증 (DB 조회 없이)
     */
    fun validateRefreshTokenSignature(token: String): Boolean =
        runCatching {
            Jwts
                .parser()
                .verifyWith(jwtProperties.refreshTokenSecretKey)
                .build()
                .parseSignedClaims(token)
        }.isSuccess

    suspend fun findRefreshToken(token: String): RefreshTokenEntity? = refreshTokenRepository.findByToken(token)

    /**
     * Token Family 전체 무효화 (jOOQ 벌크 UPDATE)
     */
    suspend fun revokeAllByFamily(familyId: UUID) {
        val refreshTokens = DSL.table("refresh_tokens")
        Mono
            .from(
                dsl
                    .update(refreshTokens)
                    .set(DSL.field("revoked", Boolean::class.java), true)
                    .set(DSL.field("updated_at", LocalDateTime::class.java), LocalDateTime.now(ZoneOffset.UTC))
                    .where(DSL.field("family_id", UUID::class.java).eq(familyId)),
            ).awaitSingle()
    }

    fun getUserIdFromAccessToken(token: String): UUID = getUserIdFromToken(token, jwtProperties.accessTokenSecretKey)

    fun getUserIdFromRefreshToken(token: String): UUID = getUserIdFromToken(token, jwtProperties.refreshTokenSecretKey)

    suspend fun revokeRefreshToken(token: String) {
        refreshTokenRepository.deleteByToken(token)
    }

    suspend fun revokeAllRefreshTokens(userId: UUID) {
        refreshTokenRepository.deleteByUserId(userId)
    }

    private fun getUserIdFromToken(
        token: String,
        secretKey: SecretKey,
    ): UUID {
        val claims =
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        return UUID.fromString(claims.subject)
    }
}
