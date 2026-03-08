package com.insuranceai.chat.controller

import com.insuranceai.chat.dto.ChatMessageRequest
import com.insuranceai.chat.dto.ChatSessionResponse
import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.CreateSessionRequest
import com.insuranceai.chat.dto.SessionMessagesResponse
import com.insuranceai.chat.error.ChatError
import com.insuranceai.chat.service.ChatService
import com.insuranceai.chat.usecase.ReplyToSessionUseCase
import com.insuranceai.chat.usecase.SendChatMessageUseCase
import com.insuranceai.common.security.UserPrincipal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChatControllerTest {
    private val chatService = mockk<ChatService>()
    private val sendChatMessageUseCase = mockk<SendChatMessageUseCase>()
    private val replyToSessionUseCase = mockk<ReplyToSessionUseCase>()
    private val controller = ChatController(chatService, sendChatMessageUseCase, replyToSessionUseCase)
    private val principal = UserPrincipal(userId = UUID.randomUUID(), nickname = "test")
    private val sessionId = UUID.randomUUID()

    private val mockSessionResponse =
        ChatSessionResponse(
            id = sessionId,
            title = "테스트 세션",
            lastMessagePreview = "안녕하세요",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `세션 생성 성공 시 201과 ChatSessionResponse 반환`() =
        runTest {
            coEvery { chatService.createSession(any(), any()) } returns mockSessionResponse

            val request = CreateSessionRequest(initialMessage = "암보험 알려주세요")
            val result = controller.createSession(request, principal)

            assertEquals(HttpStatus.CREATED, result.statusCode)
            assertTrue(result.body!!.success)
            assertEquals(sessionId, result.body!!.data!!.id)
        }

    @Test
    fun `세션 목록 조회 성공 시 200과 리스트 반환`() =
        runTest {
            coEvery { chatService.getSessions(any()) } returns listOf(mockSessionResponse)

            val result = controller.getSessions(principal)

            assertEquals(HttpStatus.OK, result.statusCode)
            assertTrue(result.body!!.success)
            assertEquals(1, result.body!!.data!!.size)
        }

    @Test
    fun `메시지 이력 조회 성공 시 200과 SessionMessagesResponse 반환`() =
        runTest {
            val mockMessages =
                SessionMessagesResponse(
                    sessionId = sessionId,
                    messages = emptyList(),
                )
            coEvery { chatService.getMessages(any(), any()) } returns mockMessages

            val result = controller.getMessages(sessionId, principal)

            assertEquals(HttpStatus.OK, result.statusCode)
            assertTrue(result.body!!.success)
            assertEquals(sessionId, result.body!!.data!!.sessionId)
        }

    @Test
    fun `세션 삭제 성공 시 204 No Content 반환`() =
        runTest {
            coEvery { chatService.deleteSession(any(), any()) } returns Unit

            val result = controller.deleteSession(sessionId, principal)

            assertEquals(HttpStatus.NO_CONTENT, result.statusCode)
            coVerify { chatService.deleteSession(sessionId, principal.userId) }
        }

    @Test
    fun `다른 사용자 세션 접근 시 ChatError NotOwner 발생`() =
        runTest {
            coEvery { chatService.getMessages(any(), any()) } throws ChatError.NotOwner

            assertFailsWith<ChatError.NotOwner> {
                controller.getMessages(sessionId, principal)
            }
        }

    @Test
    fun `SSE 스트리밍 메시지 전송 시 Flux ServerSentEvent 반환`() {
        val events =
            listOf(
                ChatStreamEvent(type = "searching", data = "검색 중..."),
                ChatStreamEvent(type = "token", data = "안녕"),
                ChatStreamEvent(type = "done", data = "완료"),
            )
        every { sendChatMessageUseCase.execute(any(), any(), any()) } returns Flux.fromIterable(events)

        val request = ChatMessageRequest(content = "암보험이 뭔가요?")
        val result = controller.sendMessage(sessionId, request, principal)

        StepVerifier
            .create(result)
            .expectNextMatches { it.data()?.type == "searching" }
            .expectNextMatches { it.data()?.type == "token" }
            .expectNextMatches { it.data()?.type == "done" }
            .verifyComplete()
    }
}
