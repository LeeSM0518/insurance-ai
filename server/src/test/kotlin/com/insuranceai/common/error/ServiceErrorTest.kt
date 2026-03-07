package com.insuranceai.common.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class ServiceErrorTest {
    // 테스트용 sealed class 에러 정의
    sealed class TestError(
        override val message: String,
    ) : ServiceError() {
        override val mainCode = "TEST"

        data object TokenExpired : TestError("토큰이 만료되었습니다") {
            override val status = HttpStatus.UNAUTHORIZED
        }

        data object SessionNotFound : TestError("세션을 찾을 수 없습니다") {
            override val status = HttpStatus.NOT_FOUND
        }

        data class InvalidInput(
            val field: String,
        ) : TestError("${field}이(가) 유효하지 않습니다") {
            override val status = HttpStatus.BAD_REQUEST
        }
    }

    @Test
    fun `data object 에러 코드 자동 생성`() {
        assertEquals("TEST:TOKEN_EXPIRED", TestError.TokenExpired.code)
    }

    @Test
    fun `3단어 에러 코드 자동 생성`() {
        assertEquals("TEST:SESSION_NOT_FOUND", TestError.SessionNotFound.code)
    }

    @Test
    fun `data class 에러 코드 자동 생성`() {
        val error = TestError.InvalidInput("email")
        assertEquals("TEST:INVALID_INPUT", error.code)
    }

    @Test
    fun `에러 메시지 확인`() {
        assertEquals("토큰이 만료되었습니다", TestError.TokenExpired.message)
        assertEquals("email이(가) 유효하지 않습니다", TestError.InvalidInput("email").message)
    }

    @Test
    fun `HTTP 상태 코드 확인`() {
        assertEquals(HttpStatus.UNAUTHORIZED, TestError.TokenExpired.status)
        assertEquals(HttpStatus.NOT_FOUND, TestError.SessionNotFound.status)
        assertEquals(HttpStatus.BAD_REQUEST, TestError.InvalidInput("email").status)
    }
}
