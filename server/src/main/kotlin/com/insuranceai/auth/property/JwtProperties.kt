package com.insuranceai.auth.property

import io.jsonwebtoken.security.Keys
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.SecretKey

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    private val accessTokenSecret: String,
    private val accessTokenExpiration: Long,
    private val refreshTokenSecret: String,
    private val refreshTokenExpiration: Long,
) {
    val accessTokenSecretKey: SecretKey =
        Keys.hmacShaKeyFor(accessTokenSecret.toByteArray())
    val refreshTokenSecretKey: SecretKey =
        Keys.hmacShaKeyFor(refreshTokenSecret.toByteArray())

    fun addAccessTokenExpiration(now: Instant): Instant = now.plus(accessTokenExpiration, ChronoUnit.MILLIS)

    fun addRefreshTokenExpiration(now: Instant): Instant = now.plus(refreshTokenExpiration, ChronoUnit.MILLIS)
}
