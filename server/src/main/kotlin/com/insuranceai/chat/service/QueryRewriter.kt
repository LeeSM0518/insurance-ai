package com.insuranceai.chat.service

import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 대화 맥락 기반 질문 재작성 서비스.
 * 후속 질문에서 생략된 맥락(보험사명, 상품명 등)을 이전 대화에서 추출하여
 * 독립적으로 이해할 수 있는 완전한 질문으로 재작성합니다.
 */
@Service
class QueryRewriter(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun rewrite(
        currentQuestion: String,
        recentMessages: List<ChatMessageEntity>,
    ): String {
        if (recentMessages.isEmpty()) return currentQuestion

        val history =
            recentMessages
                .takeLast(6) // 최근 3턴
                .joinToString("\n") { "${it.role}: ${it.content.take(200)}" }

        return try {
            val rewritten =
                suspendingChatClient.call(
                    userMessage = "대화 이력:\n$history\n\n현재 질문: $currentQuestion",
                    systemMessage = promptLoader.load("chat/query-rewrite.st"),
                )
            if (rewritten.isBlank()) {
                currentQuestion
            } else {
                log.info("질문 재작성: '{}' → '{}'", currentQuestion, rewritten)
                rewritten
            }
        } catch (e: Exception) {
            log.warn("질문 재작성 실패, 원본 사용: {}", e.message)
            currentQuestion
        }
    }
}
