package com.insuranceai.auth.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class AuthErrorTest {
    @Test
    fun `TokenMissing 에러 코드 자동 생성`() {
        assertEquals("AUTH:TOKEN_MISSING", AuthError.TokenMissing.code)
        assertEquals(HttpStatus.UNAUTHORIZED, AuthError.TokenMissing.status)
        assertEquals("인증 토큰이 없습니다", AuthError.TokenMissing.message)
    }

    @Test
    fun `TokenExpired 에러 코드 자동 생성`() {
        val error = AuthError.TokenExpired()
        assertEquals("AUTH:TOKEN_EXPIRED", error.code)
        assertEquals(HttpStatus.UNAUTHORIZED, error.status)
        assertEquals("만료된 토큰입니다", error.message)
    }

    @Test
    fun `TokenExpired 동적 메시지`() {
        val error = AuthError.TokenExpired("토큰이 10분 전에 만료되었습니다")
        assertEquals("토큰이 10분 전에 만료되었습니다", error.message)
    }

    @Test
    fun `TokenInvalid 에러 코드 자동 생성`() {
        assertEquals("AUTH:TOKEN_INVALID", AuthError.TokenInvalid.code)
        assertEquals(HttpStatus.UNAUTHORIZED, AuthError.TokenInvalid.status)
    }

    @Test
    fun `InvalidRefreshToken 에러 코드 자동 생성`() {
        val error = AuthError.InvalidRefreshToken()
        assertEquals("AUTH:INVALID_REFRESH_TOKEN", error.code)
        assertEquals(HttpStatus.UNAUTHORIZED, error.status)
    }

    @Test
    fun `KakaoAuthFailed 에러 코드 자동 생성`() {
        assertEquals("AUTH:KAKAO_AUTH_FAILED", AuthError.KakaoAuthFailed.code)
        assertEquals(HttpStatus.BAD_REQUEST, AuthError.KakaoAuthFailed.status)
    }
}
