package com.insuranceai.common.ai

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import reactor.core.publisher.Flux
import kotlin.test.assertEquals

class SuspendingChatClientTest {
    private val chatClient = mockk<ChatClient>()
    private val suspendingChatClient = SuspendingChatClient(chatClient)

    private val requestSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true)
    private val callResponseSpec = mockk<ChatClient.CallResponseSpec>(relaxed = true)
    private val streamResponseSpec = mockk<ChatClient.StreamResponseSpec>(relaxed = true)

    private fun mockChatResponse(text: String): ChatResponse {
        val metadata =
            ChatResponseMetadata
                .builder()
                .usage(DefaultUsage(10, 5))
                .build()
        return ChatResponse(listOf(Generation(AssistantMessage(text))), metadata)
    }

    private fun mockStreamChatResponse(text: String): ChatResponse {
        val metadata =
            ChatResponseMetadata
                .builder()
                .usage(DefaultUsage(0, 0))
                .build()
        return ChatResponse(listOf(Generation(AssistantMessage(text))), metadata)
    }

    @Nested
    inner class Call {
        @Test
        fun `사용자 메시지만으로 호출`() =
            runTest {
                every { chatClient.prompt() } returns requestSpec
                every { requestSpec.user(any<String>()) } returns requestSpec
                every { requestSpec.call() } returns callResponseSpec
                every { callResponseSpec.chatResponse() } returns mockChatResponse("응답 텍스트")

                val result = suspendingChatClient.call("질문")
                assertEquals("응답 텍스트", result)
            }

        @Test
        fun `시스템 메시지와 함께 호출`() =
            runTest {
                every { chatClient.prompt() } returns requestSpec
                every { requestSpec.system(any<String>()) } returns requestSpec
                every { requestSpec.user(any<String>()) } returns requestSpec
                every { requestSpec.call() } returns callResponseSpec
                every { callResponseSpec.chatResponse() } returns mockChatResponse("응답")

                val result = suspendingChatClient.call("질문", "시스템 프롬프트")
                assertEquals("응답", result)
                verify { requestSpec.system("시스템 프롬프트") }
            }

        @Test
        fun `chatResponse가 null이면 빈 문자열 반환`() =
            runTest {
                every { chatClient.prompt() } returns requestSpec
                every { requestSpec.user(any<String>()) } returns requestSpec
                every { requestSpec.call() } returns callResponseSpec
                every { callResponseSpec.chatResponse() } returns null

                val result = suspendingChatClient.call("질문")
                assertEquals("", result)
            }
    }

    @Nested
    inner class Stream {
        @Test
        fun `Flux 반환`() {
            every { chatClient.prompt() } returns requestSpec
            every { requestSpec.user(any<String>()) } returns requestSpec
            every { requestSpec.stream() } returns streamResponseSpec
            every { streamResponseSpec.chatResponse() } returns
                Flux.just(
                    mockStreamChatResponse("토큰1"),
                    mockStreamChatResponse("토큰2"),
                )

            val flux = suspendingChatClient.stream("질문")
            val tokens = flux.collectList().block()
            assertEquals(listOf("토큰1", "토큰2"), tokens)
        }

        @Test
        fun `시스템 메시지 없이 스트리밍`() {
            every { chatClient.prompt() } returns requestSpec
            every { requestSpec.user(any<String>()) } returns requestSpec
            every { requestSpec.stream() } returns streamResponseSpec
            every { streamResponseSpec.chatResponse() } returns Flux.empty()

            val flux = suspendingChatClient.stream("질문")
            val tokens = flux.collectList().block()
            assertEquals(emptyList(), tokens)
        }
    }
}
