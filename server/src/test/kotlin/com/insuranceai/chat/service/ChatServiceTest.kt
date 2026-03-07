package com.insuranceai.chat.service

import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.chat.entity.ChatMessageSourceEntity
import com.insuranceai.chat.entity.ChatSessionEntity
import com.insuranceai.chat.error.ChatError
import com.insuranceai.chat.repository.ChatMessageRepository
import com.insuranceai.chat.repository.ChatMessageSourceRepository
import com.insuranceai.chat.repository.ChatSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChatServiceTest {
    private val chatSessionRepository = mockk<ChatSessionRepository>()
    private val chatMessageRepository = mockk<ChatMessageRepository>()
    private val chatMessageSourceRepository = mockk<ChatMessageSourceRepository>()
    private val dsl = mockk<DSLContext>()
    private val service = ChatService(chatSessionRepository, chatMessageRepository, chatMessageSourceRepository, dsl)

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val now = Instant.now()

    private val session =
        ChatSessionEntity(
            id = sessionId,
            userId = userId,
            title = null,
            createdAt = now,
            updatedAt = now,
        )

    @Nested
    inner class CreateSession {
        @Test
        fun `세션 생성 + 첫 메시지 저장`() =
            runTest {
                coEvery { chatSessionRepository.save(any()) } answers {
                    firstArg<ChatSessionEntity>().copy(id = sessionId)
                }
                coEvery { chatMessageRepository.save(any()) } answers {
                    firstArg<ChatMessageEntity>().copy(id = UUID.randomUUID())
                }

                val result = service.createSession(userId, "안녕하세요")

                assertEquals(sessionId, result.id)
                assertNull(result.title)
                assertEquals("안녕하세요", result.lastMessagePreview)

                coVerify { chatMessageRepository.save(match { it.role == "USER" && it.content == "안녕하세요" }) }
            }
    }

    @Nested
    inner class GetSessions {
        @Test
        fun `세션 목록 조회 (최신순)`() =
            runTest {
                val message = ChatMessageEntity(id = UUID.randomUUID(), chatSessionId = sessionId, role = "USER", content = "질문입니다")
                coEvery { chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId) } returns flowOf(session)
                coEvery { chatMessageRepository.findByChatSessionIdOrderByCreatedAt(sessionId) } returns flowOf(message)

                val result = service.getSessions(userId)

                assertEquals(1, result.size)
                assertEquals("질문입니다", result[0].lastMessagePreview)
            }
    }

    @Nested
    inner class DeleteSession {
        @Test
        fun `세션 정상 삭제`() =
            runTest {
                coEvery { chatSessionRepository.deleteByIdAndUserId(sessionId, userId) } returns 1

                service.deleteSession(sessionId, userId)

                coVerify { chatSessionRepository.deleteByIdAndUserId(sessionId, userId) }
            }

        @Test
        fun `존재하지 않는 세션 삭제 시 SessionNotFound`() =
            runTest {
                coEvery { chatSessionRepository.deleteByIdAndUserId(sessionId, userId) } returns 0

                assertFailsWith<ChatError.SessionNotFound> {
                    service.deleteSession(sessionId, userId)
                }
            }
    }

    @Nested
    inner class ValidateSessionOwner {
        @Test
        fun `소유자 검증 성공`() =
            runTest {
                coEvery { chatSessionRepository.findById(sessionId) } returns session

                val result = service.validateSessionOwner(sessionId, userId)

                assertEquals(sessionId, result.id)
            }

        @Test
        fun `세션 없으면 SessionNotFound`() =
            runTest {
                coEvery { chatSessionRepository.findById(sessionId) } returns null

                assertFailsWith<ChatError.SessionNotFound> {
                    service.validateSessionOwner(sessionId, userId)
                }
            }

        @Test
        fun `소유자 불일치 시 NotOwner`() =
            runTest {
                coEvery { chatSessionRepository.findById(sessionId) } returns session

                assertFailsWith<ChatError.NotOwner> {
                    service.validateSessionOwner(sessionId, otherUserId)
                }
            }
    }

    @Nested
    inner class SaveUserMessage {
        @Test
        fun `사용자 메시지 저장`() =
            runTest {
                coEvery { chatMessageRepository.save(any()) } answers {
                    firstArg<ChatMessageEntity>().copy(id = UUID.randomUUID())
                }

                val result = service.saveUserMessage(sessionId, "보험 질문")

                assertEquals("USER", result.role)
                assertEquals("보험 질문", result.content)
            }
    }

    @Nested
    inner class SaveAssistantMessage {
        @Test
        fun `AI 응답 메시지 저장 + 출처 + 세션 갱신`() =
            runTest {
                val messageId = UUID.randomUUID()
                coEvery { chatMessageRepository.save(any()) } answers {
                    firstArg<ChatMessageEntity>().copy(id = messageId)
                }
                coEvery { chatMessageSourceRepository.save(any()) } answers {
                    firstArg<ChatMessageSourceEntity>().copy(id = UUID.randomUUID())
                }
                coEvery { chatSessionRepository.findById(sessionId) } returns session
                coEvery { chatSessionRepository.save(any()) } answers { firstArg() }

                val source =
                    ChatMessageSourceEntity(
                        chatMessageId = UUID.randomUUID(),
                        policyArticleId = UUID.randomUUID(),
                        relevanceScore = 0.95f,
                    )

                val result = service.saveAssistantMessage(sessionId, "AI 답변", listOf(source))

                assertEquals("ASSISTANT", result.role)
                coVerify { chatMessageSourceRepository.save(match { it.chatMessageId == messageId }) }
                coVerify { chatSessionRepository.save(any()) }
            }
    }

    @Nested
    inner class UpdateSessionTitle {
        @Test
        fun `세션 제목 업데이트`() =
            runTest {
                coEvery { chatSessionRepository.findById(sessionId) } returns session
                coEvery { chatSessionRepository.save(any()) } answers { firstArg() }

                service.updateSessionTitle(sessionId, "보험 상담")

                coVerify { chatSessionRepository.save(match { it.title == "보험 상담" }) }
            }
    }

    @Nested
    inner class GetRecentMessages {
        @Test
        fun `최근 메시지 limit 적용`() =
            runTest {
                val messages =
                    (1..5).map {
                        ChatMessageEntity(
                            id = UUID.randomUUID(),
                            chatSessionId = sessionId,
                            role = "USER",
                            content = "메시지 $it",
                        )
                    }
                coEvery { chatMessageRepository.findByChatSessionIdOrderByCreatedAt(sessionId) } returns
                    flowOf(*messages.toTypedArray())

                val result = service.getRecentMessages(sessionId, 3)

                assertEquals(3, result.size)
                assertEquals("메시지 3", result[0].content)
                assertEquals("메시지 5", result[2].content)
            }
    }
}
