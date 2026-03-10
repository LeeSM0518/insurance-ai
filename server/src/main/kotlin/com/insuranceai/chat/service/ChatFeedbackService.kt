package com.insuranceai.chat.service

import com.insuranceai.chat.domain.FeedbackCategory
import com.insuranceai.chat.domain.FeedbackType
import com.insuranceai.chat.domain.MessageRole
import com.insuranceai.chat.dto.FeedbackRequest
import com.insuranceai.chat.dto.FeedbackResponse
import com.insuranceai.chat.entity.ChatFeedbackEntity
import com.insuranceai.chat.error.ChatError
import com.insuranceai.chat.repository.ChatFeedbackRepository
import com.insuranceai.chat.repository.ChatMessageRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ChatFeedbackService(
    private val chatFeedbackRepository: ChatFeedbackRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatService: ChatService,
) {
    /** 피드백 생성/수정 (UPSERT) */
    suspend fun submitFeedback(
        messageId: UUID,
        userId: UUID,
        request: FeedbackRequest,
    ): FeedbackResponse {
        val message =
            chatMessageRepository.findById(messageId)
                ?: throw ChatError.MessageNotFound

        // 세션 소유자 검증
        chatService.validateSessionOwner(message.chatSessionId, userId)

        // ASSISTANT 메시지만 피드백 가능
        if (message.role != MessageRole.ASSISTANT.name) {
            throw ChatError.InvalidFeedback
        }

        // POSITIVE 피드백이면 category/comment 무시
        val category =
            if (request.feedbackType == FeedbackType.POSITIVE) {
                null
            } else {
                request.category
            }
        val comment =
            if (request.feedbackType == FeedbackType.POSITIVE) {
                null
            } else if (request.category == FeedbackCategory.OTHER) {
                request.comment?.take(500)
            } else {
                null
            }

        val existing = chatFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId)

        val entity =
            if (existing != null) {
                // 기존 피드백 업데이트
                chatFeedbackRepository.save(
                    existing.copy(
                        feedbackType = request.feedbackType.name,
                        category = category?.name,
                        comment = comment,
                        updatedAt = Instant.now(),
                    ),
                )
            } else {
                // 신규 피드백 생성
                chatFeedbackRepository.save(
                    ChatFeedbackEntity(
                        chatMessageId = messageId,
                        userId = userId,
                        feedbackType = request.feedbackType.name,
                        category = category?.name,
                        comment = comment,
                    ),
                )
            }

        return toResponse(entity)
    }

    /** 피드백 삭제 (토글 해제) */
    suspend fun deleteFeedback(
        messageId: UUID,
        userId: UUID,
    ) {
        val message =
            chatMessageRepository.findById(messageId)
                ?: throw ChatError.MessageNotFound

        chatService.validateSessionOwner(message.chatSessionId, userId)

        chatFeedbackRepository.deleteByChatMessageIdAndUserId(messageId, userId)
    }

    /** 특정 메시지의 내 피드백 조회 */
    suspend fun getFeedback(
        messageId: UUID,
        userId: UUID,
    ): FeedbackResponse? {
        val message =
            chatMessageRepository.findById(messageId)
                ?: throw ChatError.MessageNotFound

        chatService.validateSessionOwner(message.chatSessionId, userId)

        val entity =
            chatFeedbackRepository.findByChatMessageIdAndUserId(messageId, userId)
                ?: return null

        return toResponse(entity)
    }

    private fun toResponse(entity: ChatFeedbackEntity): FeedbackResponse =
        FeedbackResponse(
            id = entity.id!!,
            chatMessageId = entity.chatMessageId,
            feedbackType = FeedbackType.valueOf(entity.feedbackType),
            category = entity.category?.let { FeedbackCategory.valueOf(it) },
            comment = entity.comment,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
