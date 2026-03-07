package com.insuranceai.auth.controller

import com.insuranceai.auth.dto.DevLoginRequest
import com.insuranceai.auth.dto.LoginRequest
import com.insuranceai.auth.dto.LoginResponse
import com.insuranceai.auth.dto.LogoutRequest
import com.insuranceai.auth.dto.TokenRefreshRequest
import com.insuranceai.auth.dto.TokenRefreshResponse
import com.insuranceai.auth.service.AuthService
import com.insuranceai.common.dto.ServiceResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    ): ResponseEntity<ServiceResponse<LoginResponse>> {
        val result =
            if (request.code != null && request.redirectUri != null) {
                authService.loginWithCode(request.code, request.redirectUri)
            } else if (request.kakaoAccessToken != null) {
                authService.login(request.kakaoAccessToken)
            } else {
                throw com.insuranceai.auth.error.AuthError.InvalidRequest
            }
        return ResponseEntity.ok(ServiceResponse.ok(result))
    }

    @PostMapping("/refresh")
    suspend fun refresh(
        @RequestBody request: TokenRefreshRequest,
    ): ResponseEntity<ServiceResponse<TokenRefreshResponse>> {
        val result = authService.refresh(request.refreshToken)
        return ResponseEntity.ok(ServiceResponse.ok(result))
    }

    @PostMapping("/logout")
    suspend fun logout(
        @RequestBody request: LogoutRequest,
    ): ResponseEntity<ServiceResponse<Unit>> {
        authService.logout(request.refreshToken)
        return ResponseEntity.ok(ServiceResponse.ok(Unit))
    }

    @PostMapping("/dev-login")
    suspend fun devLogin(
        @RequestBody(required = false) request: DevLoginRequest?,
    ): ResponseEntity<ServiceResponse<*>> {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                    ServiceResponse.error(
                        code = "AUTH:DEV_LOGIN_DISABLED",
                        message = "개발 로그인은 local 프로필에서만 사용 가능합니다",
                        statusCode = 404,
                    ),
                )
        }
        val result = authService.devLogin(request?.name)
        return ResponseEntity.ok(ServiceResponse.ok(result))
    }
}
