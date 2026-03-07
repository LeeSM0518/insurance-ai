package com.insuranceai.auth.error

import com.insuranceai.common.error.ServiceError
import org.springframework.http.HttpStatus

sealed class AuthError(
    override val message: String,
) : ServiceError() {
    override val mainCode = "AUTH"

    data object TokenMissing : AuthError("인증 토큰이 없습니다") {
        override val status = HttpStatus.UNAUTHORIZED
    }

    data class TokenExpired(
        val detail: String = "만료된 토큰입니다",
    ) : AuthError(detail) {
        override val status = HttpStatus.UNAUTHORIZED
    }

    data object TokenInvalid : AuthError("유효하지 않은 토큰입니다") {
        override val status = HttpStatus.UNAUTHORIZED
    }

    data class InvalidRefreshToken(
        val detail: String = "유효하지 않은 리프레시 토큰입니다",
    ) : AuthError(detail) {
        override val status = HttpStatus.UNAUTHORIZED
    }

    data object KakaoAuthFailed : AuthError("카카오 인증에 실패했습니다") {
        override val status = HttpStatus.BAD_REQUEST
    }

    data object InvalidRequest : AuthError("kakaoAccessToken 또는 code/redirectUri가 필요합니다") {
        override val status = HttpStatus.BAD_REQUEST
    }
}
