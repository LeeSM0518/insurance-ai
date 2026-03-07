package com.insuranceai.auth.service

import com.insuranceai.auth.entity.RefreshTokenEntity
import com.insuranceai.auth.property.JwtProperties
import com.insuranceai.auth.repository.RefreshTokenRepository
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class TokenService(
    private val jwtProperties: JwtProperties,
    private val refreshTokenRepository: RefreshTokenRepository,
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

    suspend fun createRefreshToken(userId: UUID): String {
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

        // 기존 토큰 있으면 교체, 없으면 새로 생성
        val existing = refreshTokenRepository.findByUserId(userId)
        if (existing != null) {
            refreshTokenRepository.save(
                existing.copy(
                    token = token,
                    expiresAt = expiration,
                    updatedAt = now,
                ),
            )
        } else {
            refreshTokenRepository.save(
                RefreshTokenEntity(
                    userId = userId,
                    token = token,
                    expiresAt = expiration,
                ),
            )
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

    suspend fun validateRefreshToken(token: String): Boolean {
        val valid =
            runCatching {
                Jwts
                    .parser()
                    .verifyWith(jwtProperties.refreshTokenSecretKey)
                    .build()
                    .parseSignedClaims(token)
            }.isSuccess

        if (!valid) return false

        val stored = refreshTokenRepository.findByToken(token) ?: return false
        val userId = getUserIdFromToken(token, jwtProperties.refreshTokenSecretKey)
        return stored.userId == userId && stored.expiresAt.isAfter(Instant.now())
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
