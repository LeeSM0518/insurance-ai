package com.insuranceai.auth.service

import com.insuranceai.auth.entity.RefreshTokenEntity
import com.insuranceai.auth.property.JwtProperties
import com.insuranceai.auth.repository.RefreshTokenRepository
import io.jsonwebtoken.Jwts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TokenServiceTest {
    private lateinit var tokenService: TokenService
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var jwtProperties: JwtProperties

    // 테스트용 시크릿 (32바이트 이상)
    private val accessSecret = "test-access-secret-key-for-hmac-sha256-minimum-32-bytes"
    private val refreshSecret = "test-refresh-secret-key-for-hmac-sha256-minimum-32-bytes"

    @BeforeEach
    fun setUp() {
        refreshTokenRepository = mockk()
        jwtProperties =
            JwtProperties(
                accessTokenSecret = accessSecret,
                accessTokenExpiration = 1800000, // 30분
                refreshTokenSecret = refreshSecret,
                refreshTokenExpiration = 1209600000, // 14일
            )
        tokenService = TokenService(jwtProperties, refreshTokenRepository)
    }

    @Test
    fun `Access Token 생성 및 검증 성공`() {
        val userId = UUID.randomUUID()
        val token = tokenService.createAccessToken(userId)

        assertNotNull(token)
        assertTrue(tokenService.validateAccessToken(token))
    }

    @Test
    fun `Access Token에서 userId 추출`() {
        val userId = UUID.randomUUID()
        val token = tokenService.createAccessToken(userId)

        val extractedId = tokenService.getUserIdFromAccessToken(token)
        assertEquals(userId, extractedId)
    }

    @Test
    fun `만료된 Access Token 검증 실패`() {
        val userId = UUID.randomUUID()
        val expired = Instant.now().minus(1, ChronoUnit.HOURS)
        val token =
            Jwts
                .builder()
                .subject(userId.toString())
                .issuedAt(Date.from(expired.minus(1, ChronoUnit.HOURS)))
                .expiration(Date.from(expired))
                .signWith(jwtProperties.accessTokenSecretKey)
                .compact()

        assertFalse(tokenService.validateAccessToken(token))
    }

    @Test
    fun `잘못된 서명 Access Token 검증 실패`() {
        assertFalse(tokenService.validateAccessToken("invalid.token.here"))
    }

    @Test
    fun `Refresh Token 생성 시 기존 토큰 교체`() =
        runTest {
            val userId = UUID.randomUUID()
            val existingEntity =
                RefreshTokenEntity(
                    id = UUID.randomUUID(),
                    userId = userId,
                    token = "old-token",
                    expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                )

            coEvery { refreshTokenRepository.findByUserId(userId) } returns existingEntity
            val savedSlot = slot<RefreshTokenEntity>()
            coEvery { refreshTokenRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val token = tokenService.createRefreshToken(userId)

            assertNotNull(token)
            assertEquals(token, savedSlot.captured.token)
            assertEquals(existingEntity.id, savedSlot.captured.id)
        }

    @Test
    fun `Refresh Token 생성 시 기존 토큰 없으면 새로 생성`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { refreshTokenRepository.findByUserId(userId) } returns null
            val savedSlot = slot<RefreshTokenEntity>()
            coEvery { refreshTokenRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val token = tokenService.createRefreshToken(userId)

            assertNotNull(token)
            assertEquals(userId, savedSlot.captured.userId)
        }

    @Test
    fun `Refresh Token 검증 성공`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { refreshTokenRepository.findByUserId(userId) } returns null
            val savedSlot = slot<RefreshTokenEntity>()
            coEvery { refreshTokenRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            coEvery { refreshTokenRepository.findByToken(any()) } answers {
                savedSlot.captured
            }

            val token = tokenService.createRefreshToken(userId)
            val valid = tokenService.validateRefreshToken(token)

            assertTrue(valid)
        }

    @Test
    fun `DB에 없는 Refresh Token 검증 실패`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { refreshTokenRepository.findByUserId(userId) } returns null
            val savedSlot = slot<RefreshTokenEntity>()
            coEvery { refreshTokenRepository.save(capture(savedSlot)) } answers { savedSlot.captured }
            coEvery { refreshTokenRepository.findByToken(any()) } returns null

            val token = tokenService.createRefreshToken(userId)
            val valid = tokenService.validateRefreshToken(token)

            assertFalse(valid)
        }

    @Test
    fun `Refresh Token 무효화`() =
        runTest {
            coEvery { refreshTokenRepository.deleteByToken("some-token") } returns Unit

            tokenService.revokeRefreshToken("some-token")

            coVerify { refreshTokenRepository.deleteByToken("some-token") }
        }

    @Test
    fun `사용자의 모든 Refresh Token 무효화`() =
        runTest {
            val userId = UUID.randomUUID()
            coEvery { refreshTokenRepository.deleteByUserId(userId) } returns Unit

            tokenService.revokeAllRefreshTokens(userId)

            coVerify { refreshTokenRepository.deleteByUserId(userId) }
        }

    @Test
    fun `Refresh Token에서 userId 추출`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { refreshTokenRepository.findByUserId(userId) } returns null
            coEvery { refreshTokenRepository.save(any()) } answers { firstArg() }

            val token = tokenService.createRefreshToken(userId)
            val extractedId = tokenService.getUserIdFromRefreshToken(token)

            assertEquals(userId, extractedId)
        }
}
