package com.insuranceai.chat.service

import com.insuranceai.chat.domain.MessageRole
import com.insuranceai.chat.dto.ChatMessageResponse
import com.insuranceai.chat.dto.ChatSessionResponse
import com.insuranceai.chat.dto.MessageSourceResponse
import com.insuranceai.chat.dto.SessionMessagesResponse
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.chat.entity.ChatMessageSourceEntity
import com.insuranceai.chat.entity.ChatSessionEntity
import com.insuranceai.chat.error.ChatError
import com.insuranceai.chat.repository.ChatMessageRepository
import com.insuranceai.chat.repository.ChatMessageSourceRepository
import com.insuranceai.chat.repository.ChatSessionRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.UUID

@Service
class ChatService(
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatMessageSourceRepository: ChatMessageSourceRepository,
    private val dsl: DSLContext,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 세션 생성 + 첫 메시지 저장 */
    suspend fun createSession(
        userId: UUID,
        initialMessage: String,
    ): ChatSessionResponse {
        val session =
            chatSessionRepository.save(
                ChatSessionEntity(
                    userId = userId,
                ),
            )

        // 첫 번째 USER 메시지 저장
        chatMessageRepository.save(
            ChatMessageEntity(
                chatSessionId = session.id!!,
                role = MessageRole.USER.name,
                content = initialMessage,
            ),
        )

        return toSessionResponse(session, initialMessage)
    }

    /** 세션 목록 조회 (최신순) */
    suspend fun getSessions(userId: UUID): List<ChatSessionResponse> {
        val sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).toList()

        return sessions.map { session ->
            // 마지막 메시지 프리뷰 조회
            val messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAt(session.id!!).toList()
            val lastMessage = messages.lastOrNull()
            toSessionResponse(session, lastMessage?.content)
        }
    }

    /** 세션 삭제 (소유자 검증 포함) */
    suspend fun deleteSession(
        sessionId: UUID,
        userId: UUID,
    ) {
        val deletedCount = chatSessionRepository.deleteByIdAndUserId(sessionId, userId)
        if (deletedCount == 0) {
            throw ChatError.SessionNotFound
        }
    }

    /** 세션 소유자 검증 */
    suspend fun validateSessionOwner(
        sessionId: UUID,
        userId: UUID,
    ): ChatSessionEntity {
        val session =
            chatSessionRepository.findById(sessionId)
                ?: throw ChatError.SessionNotFound

        if (session.userId != userId) {
            throw ChatError.NotOwner
        }

        return session
    }

    /** 메시지 이력 조회 (출처 포함) */
    suspend fun getMessages(
        sessionId: UUID,
        userId: UUID,
    ): SessionMessagesResponse {
        validateSessionOwner(sessionId, userId)

        val messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAt(sessionId).toList()

        val messageResponses =
            messages.map { message ->
                val sources =
                    if (message.role == MessageRole.ASSISTANT.name) {
                        getMessageSourceDetails(message.id!!)
                    } else {
                        emptyList()
                    }

                ChatMessageResponse(
                    id = message.id!!,
                    role = message.role,
                    content = message.content,
                    sources = sources,
                    createdAt = message.createdAt,
                )
            }

        return SessionMessagesResponse(
            sessionId = sessionId,
            messages = messageResponses,
        )
    }

    /** 사용자 메시지 저장 */
    suspend fun saveUserMessage(
        sessionId: UUID,
        content: String,
    ): ChatMessageEntity =
        chatMessageRepository.save(
            ChatMessageEntity(
                chatSessionId = sessionId,
                role = MessageRole.USER.name,
                content = content,
            ),
        )

    /** AI 응답 메시지 저장 (출처 포함) */
    suspend fun saveAssistantMessage(
        sessionId: UUID,
        content: String,
        sources: List<ChatMessageSourceEntity>,
    ): ChatMessageEntity {
        val message =
            chatMessageRepository.save(
                ChatMessageEntity(
                    chatSessionId = sessionId,
                    role = MessageRole.ASSISTANT.name,
                    content = content,
                ),
            )

        // 출처 정보 저장
        for (source in sources) {
            chatMessageSourceRepository.save(
                source.copy(chatMessageId = message.id!!),
            )
        }

        // 세션 updatedAt 갱신
        val session = chatSessionRepository.findById(sessionId)
        if (session != null) {
            chatSessionRepository.save(session.copy(updatedAt = Instant.now()))
        }

        return message
    }

    /** 세션 제목 업데이트 */
    suspend fun updateSessionTitle(
        sessionId: UUID,
        title: String,
    ) {
        val session = chatSessionRepository.findById(sessionId) ?: return
        chatSessionRepository.save(session.copy(title = title, updatedAt = Instant.now()))
    }

    /** 세션의 마지막 USER 메시지 조회 */
    suspend fun getLastUserMessage(sessionId: UUID): ChatMessageEntity {
        val messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAt(sessionId).toList()
        return messages.lastOrNull { it.role == MessageRole.USER.name }
            ?: throw ChatError.MessageNotFound
    }

    /** 최근 대화 이력 조회 (RAG 컨텍스트용) */
    suspend fun getRecentMessages(
        sessionId: UUID,
        limit: Int = 20,
    ): List<ChatMessageEntity> {
        val allMessages = chatMessageRepository.findByChatSessionIdOrderByCreatedAt(sessionId).toList()
        return allMessages.takeLast(limit)
    }

    /** jOOQ JOIN으로 출처 상세 정보 조회 */
    private suspend fun getMessageSourceDetails(messageId: UUID): List<MessageSourceResponse> {
        val cms = DSL.table("chat_message_sources")
        val pa = DSL.table("policy_articles")
        val p = DSL.table("products")
        val ic = DSL.table("insurance_companies")

        val query =
            dsl
                .select(
                    DSL.field("$cms.policy_article_id").`as`("policy_article_id"),
                    DSL.field("$ic.name").`as`("company_name"),
                    DSL.field("$p.name").`as`("product_name"),
                    DSL.field("$pa.content_type").`as`("content_type"),
                    DSL.field("$pa.article_number").`as`("article_number"),
                    DSL.field("$pa.article_title").`as`("article_title"),
                    DSL.field("$cms.relevance_score").`as`("relevance_score"),
                ).from(cms)
                .join(pa)
                .on(DSL.field("$cms.policy_article_id").eq(DSL.field("$pa.id")))
                .join(p)
                .on(DSL.field("$pa.product_id").eq(DSL.field("$p.id")))
                .join(ic)
                .on(DSL.field("$p.insurance_company_id").eq(DSL.field("$ic.id")))
                .where(DSL.field("$cms.chat_message_id").eq(messageId))

        return Flux
            .from(query)
            .collectList()
            .awaitSingle()
            .map { record ->
                MessageSourceResponse(
                    articleId = record.get("policy_article_id", UUID::class.java),
                    insuranceCompany = record.get("company_name", String::class.java),
                    productName = record.get("product_name", String::class.java),
                    contentType = record.get("content_type", String::class.java) ?: "",
                    articleNumber = record.get("article_number", String::class.java),
                    articleTitle = record.get("article_title", String::class.java),
                    relevanceScore = record.get("relevance_score", Float::class.java),
                )
            }
    }

    private fun toSessionResponse(
        session: ChatSessionEntity,
        lastMessage: String?,
    ): ChatSessionResponse =
        ChatSessionResponse(
            id = session.id!!,
            title = session.title,
            lastMessagePreview = lastMessage?.take(100),
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
        )
}
