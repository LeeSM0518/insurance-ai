package com.insuranceai.chat.service

import com.insuranceai.chat.domain.FeedbackCategory
import com.insuranceai.chat.domain.FeedbackType
import com.insuranceai.chat.domain.MessageRole
import com.insuranceai.chat.dto.FeedbackRequest
import com.insuranceai.chat.entity.ChatFeedbackEntity
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.chat.entity.ChatSessionEntity
import com.insuranceai.chat.error.ChatError
import com.insuranceai.chat.repository.ChatFeedbackRepository
import com.insuranceai.chat.repository.ChatMessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChatFeedbackServiceTest {
    private val chatFeedbackRepository = mockk<ChatFeedbackRepository>()
    private val chatMessageRepository = mockk<ChatMessageRepository>()
    private val chatService = mockk<ChatService>()
    private val service = ChatFeedbackService(chatFeedbackRepository, chatMessageRepository, chatService)

    private val userId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()
    private val now = Instant.now()

    private val session =
        ChatSessionEntity(
            id = sessionId,
            userId = userId,
            createdAt = now,
            updatedAt = now,
        )

    private val assistantMessage =
        ChatMessageEntity(
            id = messageId,
            chatSessionId = sessionId,
            role = MessageRole.ASSISTANT.name,
            content = "AI 답변입니다",
            createdAt = now,
        )

    private val userMessage =
        ChatMessageEntity(
            id = messageId,
            chatSessionId = sessionId,
            role = MessageRole.USER.name,
            content = "사용자 질문입니다",
            createdAt = now,
        )

    @Nested
    inner class SubmitFeedback {
        @Test
        fun `긍정 피드백 저장`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns assistantMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } returns session
                coEvery { chatFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId) } returns null
                coEvery { chatFeedbackRepository.save(any()) } answers {
                    firstArg<ChatFeedbackEntity>().copy(id = UUID.randomUUID())
                }

                val result =
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(feedbackType = FeedbackType.POSITIVE),
                    )

                assertEquals(FeedbackType.POSITIVE, result.feedbackType)
                assertNull(result.category)
                assertNull(result.comment)
            }

        @Test
        fun `부정 피드백 저장 (카테고리 포함)`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns assistantMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } returns session
                coEvery { chatFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId) } returns null
                coEvery { chatFeedbackRepository.save(any()) } answers {
                    firstArg<ChatFeedbackEntity>().copy(id = UUID.randomUUID())
                }

                val result =
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(
                            feedbackType = FeedbackType.NEGATIVE,
                            category = FeedbackCategory.INACCURATE,
                        ),
                    )

                assertEquals(FeedbackType.NEGATIVE, result.feedbackType)
                assertEquals(FeedbackCategory.INACCURATE, result.category)
                assertNull(result.comment)
            }

        @Test
        fun `기타 카테고리 + 코멘트`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns assistantMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } returns session
                coEvery { chatFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId) } returns null
                coEvery { chatFeedbackRepository.save(any()) } answers {
                    firstArg<ChatFeedbackEntity>().copy(id = UUID.randomUUID())
                }

                val result =
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(
                            feedbackType = FeedbackType.NEGATIVE,
                            category = FeedbackCategory.OTHER,
                            comment = "답변이 너무 길어요",
                        ),
                    )

                assertEquals(FeedbackType.NEGATIVE, result.feedbackType)
                assertEquals(FeedbackCategory.OTHER, result.category)
                assertEquals("답변이 너무 길어요", result.comment)
            }

        @Test
        fun `피드백 수정 (POSITIVE → NEGATIVE)`() =
            runTest {
                val existingFeedback =
                    ChatFeedbackEntity(
                        id = UUID.randomUUID(),
                        chatMessageId = messageId,
                        userId = userId,
                        feedbackType = FeedbackType.POSITIVE.name,
                        createdAt = now,
                        updatedAt = now,
                    )

                coEvery { chatMessageRepository.findById(messageId) } returns assistantMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } returns session
                coEvery { chatFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId) } returns existingFeedback
                coEvery { chatFeedbackRepository.save(any()) } answers { firstArg() }

                val result =
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(
                            feedbackType = FeedbackType.NEGATIVE,
                            category = FeedbackCategory.INSUFFICIENT,
                        ),
                    )

                assertEquals(FeedbackType.NEGATIVE, result.feedbackType)
                assertEquals(FeedbackCategory.INSUFFICIENT, result.category)
            }

        @Test
        fun `존재하지 않는 메시지`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns null

                assertFailsWith<ChatError.MessageNotFound> {
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(feedbackType = FeedbackType.POSITIVE),
                    )
                }
            }

        @Test
        fun `타인 세션 메시지에 피드백`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns assistantMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } throws ChatError.NotOwner

                assertFailsWith<ChatError.NotOwner> {
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(feedbackType = FeedbackType.POSITIVE),
                    )
                }
            }

        @Test
        fun `USER 메시지에 피드백 시도`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns userMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } returns session

                assertFailsWith<ChatError.InvalidFeedback> {
                    service.submitFeedback(
                        messageId,
                        userId,
                        FeedbackRequest(feedbackType = FeedbackType.POSITIVE),
                    )
                }
            }
    }

    @Nested
    inner class DeleteFeedback {
        @Test
        fun `피드백 삭제`() =
            runTest {
                coEvery { chatMessageRepository.findById(messageId) } returns assistantMessage
                coEvery { chatService.validateSessionOwner(sessionId, userId) } returns session
                coEvery { chatFeedbackRepository.deleteByChatMessageIdAndUserId(messageId, userId) } returns 1

                service.deleteFeedback(messageId, userId)

                coVerify { chatFeedbackRepository.deleteByChatMessageIdAndUserId(messageId, userId) }
            }
    }
}
