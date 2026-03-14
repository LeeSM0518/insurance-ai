package com.insuranceai.common.ai

import com.insuranceai.common.config.LlmRetryConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.client.ChatClient
import reactor.core.publisher.Flux
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuspendingChatClientRetryTest {
    private val chatClient = mockk<ChatClient>()
    private val retryConfig =
        LlmRetryConfig(
            maxRetries = 3,
            initialDelayMs = 10,
            maxDelayMs = 100,
            multiplier = 2.0,
        )
    private val suspendingChatClient = SuspendingChatClient(chatClient, retryConfig)

    private val requestSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true)
    private val callResponseSpec = mockk<ChatClient.CallResponseSpec>(relaxed = true)
    private val streamResponseSpec = mockk<ChatClient.StreamResponseSpec>(relaxed = true)

    @Nested
    inner class IsRateLimitError {
        @Test
        fun `429 포함 메시지는 rate limit 에러로 판별`() {
            val error = RuntimeException("Response exception, Status: [429 TOO_MANY_REQUESTS]")
            assertTrue(suspendingChatClient.isRateLimitError(error))
        }

        @Test
        fun `rate_limit 포함 메시지는 rate limit 에러로 판별`() {
            val error = RuntimeException("rate_limit_exceeded: too many requests")
            assertTrue(suspendingChatClient.isRateLimitError(error))
        }

        @Test
        fun `다른 에러는 rate limit 아님`() {
            val error = RuntimeException("Response exception, Status: [500 INTERNAL_SERVER_ERROR]")
            assertFalse(suspendingChatClient.isRateLimitError(error))
        }

        @Test
        fun `null 메시지는 rate limit 아님`() {
            val error = RuntimeException()
            assertFalse(suspendingChatClient.isRateLimitError(error))
        }
    }

    @Nested
    inner class CallRetry {
        @Test
        fun `429 에러 시 재시도 후 성공`() =
            runTest {
                every { chatClient.prompt() } returns requestSpec
                every { requestSpec.user(any<String>()) } returns requestSpec
                every { requestSpec.call() } returns callResponseSpec

                var callCount = 0
                every { callResponseSpec.content() } answers {
                    callCount++
                    if (callCount < 3) {
                        throw RuntimeException("Response exception, Status: [429 TOO_MANY_REQUESTS]")
                    }
                    "성공 응답"
                }

                val result = suspendingChatClient.call("질문")
                assertEquals("성공 응답", result)
                assertEquals(3, callCount)
            }

        @Test
        fun `429 아닌 에러는 즉시 전파`() =
            runTest {
                every { chatClient.prompt() } returns requestSpec
                every { requestSpec.user(any<String>()) } returns requestSpec
                every { requestSpec.call() } returns callResponseSpec
                every { callResponseSpec.content() } throws
                    RuntimeException("Response exception, Status: [400 BAD_REQUEST]")

                val exception =
                    assertThrows<RuntimeException> {
                        suspendingChatClient.call("질문")
                    }
                assertTrue(exception.message!!.contains("400"))
                // call()은 1번만 호출되어야 함 (재시도 없음)
                verify(exactly = 1) { callResponseSpec.content() }
            }

        @Test
        fun `최대 재시도 초과 시 마지막 예외 전파`() =
            runTest {
                every { chatClient.prompt() } returns requestSpec
                every { requestSpec.user(any<String>()) } returns requestSpec
                every { requestSpec.call() } returns callResponseSpec
                every { callResponseSpec.content() } throws
                    RuntimeException("Response exception, Status: [429 TOO_MANY_REQUESTS]")

                val exception =
                    assertThrows<RuntimeException> {
                        suspendingChatClient.call("질문")
                    }
                assertTrue(exception.message!!.contains("429"))
            }
    }

    @Nested
    inner class CalculateDelay {
        @Test
        fun `지수 백오프 대기 시간이 범위 내`() {
            // attempt 1: 기본 10ms, jitter ±20% → 8~12ms
            val delay1 = suspendingChatClient.calculateDelay(1)
            assertTrue(delay1 in 10..12, "attempt 1 delay=$delay1, expected 10~12")

            // attempt 2: 기본 20ms, jitter ±20% → 16~24ms, coerceIn(10, 100)
            val delay2 = suspendingChatClient.calculateDelay(2)
            assertTrue(delay2 in 10..100, "attempt 2 delay=$delay2, expected 10~100")

            // attempt 3: 기본 40ms, jitter ±20% → 32~48ms, coerceIn(10, 100)
            val delay3 = suspendingChatClient.calculateDelay(3)
            assertTrue(delay3 in 10..100, "attempt 3 delay=$delay3, expected 10~100")
        }

        @Test
        fun `최대 대기 시간을 초과하지 않음`() {
            val delay = suspendingChatClient.calculateDelay(20)
            assertTrue(delay <= retryConfig.maxDelayMs, "delay=$delay, maxDelay=${retryConfig.maxDelayMs}")
        }
    }

    @Nested
    inner class StreamRetry {
        @Test
        fun `스트리밍 429 재시도 후 성공`() {
            every { chatClient.prompt() } returns requestSpec
            every { requestSpec.user(any<String>()) } returns requestSpec
            every { requestSpec.stream() } returns streamResponseSpec

            var callCount = 0
            every { streamResponseSpec.content() } answers {
                callCount++
                if (callCount < 2) {
                    Flux.error(RuntimeException("Response exception, Status: [429 TOO_MANY_REQUESTS]"))
                } else {
                    Flux.just("토큰1", "토큰2")
                }
            }

            val flux = suspendingChatClient.stream("질문")
            val tokens = flux.collectList().block()
            assertEquals(listOf("토큰1", "토큰2"), tokens)
        }

        @Test
        fun `스트리밍 429 아닌 에러는 즉시 전파`() {
            every { chatClient.prompt() } returns requestSpec
            every { requestSpec.user(any<String>()) } returns requestSpec
            every { requestSpec.stream() } returns streamResponseSpec
            every { streamResponseSpec.content() } returns
                Flux.error(RuntimeException("Response exception, Status: [500 INTERNAL_SERVER_ERROR]"))

            val flux = suspendingChatClient.stream("질문")
            assertThrows<RuntimeException> {
                flux.collectList().block()
            }
        }
    }
}
