package com.insuranceai.chat.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class ChatErrorTest {
    @Test
    fun `SessionNotFound 에러 코드 자동 생성`() {
        assertEquals("CHAT:SESSION_NOT_FOUND", ChatError.SessionNotFound.code)
        assertEquals(HttpStatus.NOT_FOUND, ChatError.SessionNotFound.status)
        assertEquals("세션을 찾을 수 없습니다", ChatError.SessionNotFound.message)
    }

    @Test
    fun `NotOwner 에러 코드 자동 생성`() {
        assertEquals("CHAT:NOT_OWNER", ChatError.NotOwner.code)
        assertEquals(HttpStatus.FORBIDDEN, ChatError.NotOwner.status)
    }

    @Test
    fun `RateLimitExceeded 에러 코드 자동 생성`() {
        assertEquals("CHAT:RATE_LIMIT_EXCEEDED", ChatError.RateLimitExceeded.code)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ChatError.RateLimitExceeded.status)
    }

    @Test
    fun `LlmResponseFailed 에러 코드 자동 생성`() {
        assertEquals("CHAT:LLM_RESPONSE_FAILED", ChatError.LlmResponseFailed.code)
        assertEquals(HttpStatus.BAD_GATEWAY, ChatError.LlmResponseFailed.status)
    }
}
