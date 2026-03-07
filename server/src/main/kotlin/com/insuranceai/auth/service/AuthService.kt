package com.insuranceai.auth.service

import com.insuranceai.auth.dto.LoginResponse
import com.insuranceai.auth.dto.TokenRefreshResponse
import com.insuranceai.auth.dto.UserResponse
import com.insuranceai.auth.entity.UserEntity
import com.insuranceai.auth.error.AuthError
import com.insuranceai.auth.property.JwtProperties
import com.insuranceai.auth.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthService(
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val userRepository: UserRepository,
    private val tokenService: TokenService,
    private val jwtProperties: JwtProperties,
) {
    suspend fun login(kakaoAccessToken: String): LoginResponse {
        val kakaoUserInfo = kakaoOAuthClient.getUserInfo(kakaoAccessToken)
        val user = findOrCreateUser("KAKAO", kakaoUserInfo.id.toString(), kakaoUserInfo.nickname, kakaoUserInfo.email)
        return issueTokens(user)
    }

    suspend fun loginWithCode(code: String, redirectUri: String): LoginResponse {
        val kakaoAccessToken = kakaoOAuthClient.exchangeCode(code, redirectUri)
        return login(kakaoAccessToken)
    }

    suspend fun refresh(refreshToken: String): TokenRefreshResponse {
        if (!tokenService.validateRefreshToken(refreshToken)) {
            throw AuthError.InvalidRefreshToken()
        }
        val userId = tokenService.getUserIdFromRefreshToken(refreshToken)
        val accessToken = tokenService.createAccessToken(userId)
        val newRefreshToken = tokenService.createRefreshToken(userId)
        return TokenRefreshResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            expiresIn = jwtProperties.addAccessTokenExpiration(Instant.now()).epochSecond - Instant.now().epochSecond,
        )
    }

    suspend fun logout(refreshToken: String) {
        tokenService.revokeRefreshToken(refreshToken)
    }

    suspend fun devLogin(name: String?): LoginResponse {
        val user = findOrCreateUser("DEV", "dev-user", name ?: "개발자", null)
        return issueTokens(user)
    }

    private suspend fun findOrCreateUser(
        provider: String,
        providerId: String,
        nickname: String,
        email: String?,
    ): UserEntity {
        val existing = userRepository.findByProviderAndProviderId(provider, providerId)
        return if (existing != null) {
            userRepository.save(existing.copy(lastLoginAt = Instant.now(), updatedAt = Instant.now()))
        } else {
            userRepository.save(
                UserEntity(
                    provider = provider,
                    providerId = providerId,
                    nickname = nickname,
                    email = email,
                ),
            )
        }
    }

    private suspend fun issueTokens(user: UserEntity): LoginResponse {
        val userId = user.id!!
        val accessToken = tokenService.createAccessToken(userId)
        val refreshToken = tokenService.createRefreshToken(userId)
        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtProperties.addAccessTokenExpiration(Instant.now()).epochSecond - Instant.now().epochSecond,
            user = UserResponse.from(user),
        )
    }
}
