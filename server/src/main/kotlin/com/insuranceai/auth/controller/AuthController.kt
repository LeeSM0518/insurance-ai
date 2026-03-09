package com.insuranceai.auth.controller

import com.insuranceai.auth.dto.DevLoginRequest
import com.insuranceai.auth.dto.LoginRequest
import com.insuranceai.auth.dto.LoginResponse
import com.insuranceai.auth.dto.LogoutRequest
import com.insuranceai.auth.dto.TokenRefreshRequest
import com.insuranceai.auth.dto.TokenRefreshResponse
import com.insuranceai.auth.error.AuthError
import com.insuranceai.auth.service.AuthService
import com.insuranceai.common.dto.ServiceResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "카카오 OAuth 로그인, 토큰 관리 API")
class AuthController(
    private val authService: AuthService,
    private val environment: Environment,
) {
    @PostMapping("/login")
    suspend fun login(
        @RequestBody request: LoginRequest,
    ): ServiceResponse<LoginResponse> {
        val result =
            if (request.code != null && request.redirectUri != null) {
                authService.loginWithCode(request.code, request.redirectUri)
            } else if (request.kakaoAccessToken != null) {
                authService.login(request.kakaoAccessToken)
            } else {
                throw AuthError.InvalidRequest
            }
        return ServiceResponse.ok(result)
    }

    @PostMapping("/refresh")
    suspend fun refresh(
        @RequestBody request: TokenRefreshRequest,
    ): ServiceResponse<TokenRefreshResponse> {
        val result = authService.refresh(request.refreshToken)
        return ServiceResponse.ok(result)
    }

    @PostMapping("/logout")
    suspend fun logout(
        @RequestBody request: LogoutRequest,
    ): ServiceResponse<Unit> {
        authService.logout(request.refreshToken)
        return ServiceResponse.ok(Unit)
    }

    @PostMapping("/dev-login")
    suspend fun devLogin(
        @RequestBody(required = false) request: DevLoginRequest?,
    ): ServiceResponse<*> {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw AuthError.DevLoginDisabled
        }
        val result = authService.devLogin(request?.name)
        return ServiceResponse.ok(result)
    }
}
