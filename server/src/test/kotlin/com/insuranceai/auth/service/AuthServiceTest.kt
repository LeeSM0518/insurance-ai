package com.insuranceai.auth.service

import com.insuranceai.auth.entity.UserEntity
import com.insuranceai.auth.error.AuthError
import com.insuranceai.auth.property.JwtProperties
import com.insuranceai.auth.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AuthServiceTest {
    private lateinit var authService: AuthService
    private lateinit var kakaoOAuthClient: KakaoOAuthClient
    private lateinit var userRepository: UserRepository
    private lateinit var tokenService: TokenService
    private lateinit var jwtProperties: JwtProperties

    @BeforeEach
    fun setUp() {
        kakaoOAuthClient = mockk()
        userRepository = mockk()
        tokenService = mockk()
        jwtProperties =
            JwtProperties(
                accessTokenSecret = "test-access-secret-key-for-hmac-sha256-minimum-32-bytes",
                accessTokenExpiration = 1800000,
                refreshTokenSecret = "test-refresh-secret-key-for-hmac-sha256-minimum-32-bytes",
                refreshTokenExpiration = 1209600000,
            )
        authService = AuthService(kakaoOAuthClient, userRepository, tokenService, jwtProperties)
    }

    @Test
    fun `신규 사용자 카카오 로그인`() =
        runTest {
            val userId = UUID.randomUUID()
            val kakaoUserInfo = KakaoUserInfo(id = 12345L, nickname = "홍길동", email = "hong@test.com")

            coEvery { kakaoOAuthClient.getUserInfo("kakao-token") } returns kakaoUserInfo
            coEvery { userRepository.findByProviderAndProviderId("KAKAO", "12345") } returns null
            val savedSlot = slot<UserEntity>()
            coEvery { userRepository.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = userId)
            }
            coEvery { tokenService.createAccessToken(userId) } returns "access-token"
            coEvery { tokenService.createRefreshToken(userId) } returns "refresh-token"

            val result = authService.login("kakao-token")

            assertEquals("access-token", result.accessToken)
            assertEquals("refresh-token", result.refreshToken)
            assertEquals("홍길동", result.user.nickname)
            assertEquals("hong@test.com", result.user.email)
        }

    @Test
    fun `기존 사용자 카카오 로그인 - lastLoginAt 업데이트`() =
        runTest {
            val userId = UUID.randomUUID()
            val existingUser =
                UserEntity(
                    id = userId,
                    provider = "KAKAO",
                    providerId = "12345",
                    nickname = "홍길동",
                    email = "hong@test.com",
                )
            val kakaoUserInfo = KakaoUserInfo(id = 12345L, nickname = "홍길동", email = "hong@test.com")

            coEvery { kakaoOAuthClient.getUserInfo("kakao-token") } returns kakaoUserInfo
            coEvery { userRepository.findByProviderAndProviderId("KAKAO", "12345") } returns existingUser
            coEvery { userRepository.save(any()) } answers { firstArg() }
            coEvery { tokenService.createAccessToken(userId) } returns "access-token"
            coEvery { tokenService.createRefreshToken(userId) } returns "refresh-token"

            val result = authService.login("kakao-token")

            assertEquals("access-token", result.accessToken)
            coVerify { userRepository.save(match { it.id == userId }) }
        }

    @Test
    fun `토큰 갱신 성공`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { tokenService.validateRefreshToken("old-refresh") } returns true
            coEvery { tokenService.getUserIdFromRefreshToken("old-refresh") } returns userId
            coEvery { tokenService.createAccessToken(userId) } returns "new-access"
            coEvery { tokenService.createRefreshToken(userId) } returns "new-refresh"

            val result = authService.refresh("old-refresh")

            assertEquals("new-access", result.accessToken)
            assertEquals("new-refresh", result.refreshToken)
        }

    @Test
    fun `유효하지 않은 Refresh Token으로 갱신 시 에러`() =
        runTest {
            coEvery { tokenService.validateRefreshToken("invalid-token") } returns false

            assertFailsWith<AuthError.InvalidRefreshToken> {
                authService.refresh("invalid-token")
            }
        }

    @Test
    fun `로그아웃 - Refresh Token 삭제`() =
        runTest {
            coEvery { tokenService.revokeRefreshToken("refresh-token") } returns Unit

            authService.logout("refresh-token")

            coVerify { tokenService.revokeRefreshToken("refresh-token") }
        }

    @Test
    fun `dev-login 신규 사용자 생성`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { userRepository.findByProviderAndProviderId("DEV", "dev-user") } returns null
            val savedSlot = slot<UserEntity>()
            coEvery { userRepository.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = userId)
            }
            coEvery { tokenService.createAccessToken(userId) } returns "dev-access"
            coEvery { tokenService.createRefreshToken(userId) } returns "dev-refresh"

            val result = authService.devLogin("테스터")

            assertEquals("dev-access", result.accessToken)
            assertEquals("테스터", savedSlot.captured.nickname)
        }

    @Test
    fun `dev-login 이름 미지정 시 기본값 사용`() =
        runTest {
            val userId = UUID.randomUUID()

            coEvery { userRepository.findByProviderAndProviderId("DEV", "dev-user") } returns null
            val savedSlot = slot<UserEntity>()
            coEvery { userRepository.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = userId)
            }
            coEvery { tokenService.createAccessToken(userId) } returns "dev-access"
            coEvery { tokenService.createRefreshToken(userId) } returns "dev-refresh"

            val result = authService.devLogin(null)

            assertNotNull(result)
            assertEquals("개발자", savedSlot.captured.nickname)
        }
}
