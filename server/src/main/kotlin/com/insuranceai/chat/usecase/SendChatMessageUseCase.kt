package com.insuranceai.chat.usecase

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.SearchingEvent
import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.chat.dto.SourcesFoundEvent
import com.insuranceai.chat.entity.ChatMessageSourceEntity
import com.insuranceai.chat.service.ChatPromptBuilder
import com.insuranceai.chat.service.ChatService
import com.insuranceai.chat.service.ContextBuilder
import com.insuranceai.chat.service.QuestionAnalyzer
import com.insuranceai.chat.service.StreamingService
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.service.SourceVerificationService
import com.insuranceai.search.service.SearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

@Component
class SendChatMessageUseCase(
    private val chatService: ChatService,
    private val questionAnalyzer: QuestionAnalyzer,
    private val searchService: SearchService,
    private val contextBuilder: ContextBuilder,
    private val chatPromptBuilder: ChatPromptBuilder,
    private val streamingService: StreamingService,
    private val suspendingChatClient: SuspendingChatClient,
    private val sourceVerificationService: SourceVerificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(
        sessionId: UUID,
        message: String,
        userId: UUID,
    ): Flux<ChatStreamEvent> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<ChatStreamEvent>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 세션 검증
                chatService.validateSessionOwner(sessionId, userId)

                // 2. 사용자 메시지 저장
                chatService.saveUserMessage(sessionId, message)

                // 3. 질문 분석
                val analysis = questionAnalyzer.analyze(message)

                // 4. 하이브리드 검색
                sink.tryEmitNext(ChatStreamEvent("searching", SearchingEvent("검색 중...")))
                val searchResults = searchService.searchForRag(message, analysis)

                // 검색 결과에서 SourceInfo 추출
                val sources =
                    searchResults.map { result ->
                        SourceInfo(
                            articleId = result.articleId,
                            insuranceCompany = result.companyName,
                            productId = result.productId,
                            productName = result.productName,
                            contentType = result.contentType,
                            articleNumber = result.articleNumber,
                            articleTitle = result.articleTitle,
                            relevanceScore = result.relevanceScore,
                            dataSource = result.dataSource ?: "",
                        )
                    }
                sink.tryEmitNext(ChatStreamEvent("sources_found", SourcesFoundEvent(sources)))

                // 5. 대화 이력
                val history = chatService.getRecentMessages(sessionId)

                // 6. 컨텍스트 구성
                val contextResult = contextBuilder.build(searchResults, analysis, history)

                // 7. 프롬프트 구성
                val chatPrompt = chatPromptBuilder.build(message, contextResult, analysis.isComparison)

                // 8. 첫 메시지 여부 확인
                val isFirstMessage = chatService.isFirstUserMessage(sessionId)

                // 9. 스트리밍
                streamingService
                    .stream(
                        chatPrompt = chatPrompt,
                        sources = sources,
                        titleGenerator =
                            if (isFirstMessage) {
                                { generateTitle(message) }
                            } else {
                                null
                            },
                        onComplete = { fullResponse, messageId, title ->
                            // 10. AI 응답 메시지 저장
                            try {
                                chatService.saveAssistantMessage(
                                    sessionId = sessionId,
                                    content = fullResponse,
                                    sources =
                                        sources.map { source ->
                                            ChatMessageSourceEntity(
                                                chatMessageId = UUID.randomUUID(), // saveAssistantMessage에서 재설정됨
                                                policyArticleId = source.articleId,
                                                relevanceScore = source.relevanceScore,
                                            )
                                        },
                                )
                            } catch (e: Exception) {
                                log.error("AI 응답 메시지 저장 실패", e)
                            }

                            // 11. 출처 자동 검증 (비차단)
                            try {
                                val verification = sourceVerificationService.verify(fullResponse, sources)
                                if (!verification.isValid) {
                                    log.warn("출처 불일치 감지: sessionId={}, mismatches={}", sessionId, verification.mismatches)
                                }
                            } catch (e: Exception) {
                                log.warn("출처 검증 실패 (비차단): {}", e.message)
                            }

                            // 12. 세션 제목 DB 저장
                            if (title != null) {
                                chatService.updateSessionTitle(sessionId, title)
                            }
                        },
                    ).doOnNext { event -> sink.tryEmitNext(event) }
                    .doOnComplete { sink.tryEmitComplete() }
                    .doOnError { e ->
                        log.error("스트리밍 에러", e)
                        sink.tryEmitComplete()
                    }.subscribe()
            } catch (e: Exception) {
                log.error("RAG 파이프라인 에러", e)
                sink.tryEmitNext(
                    ChatStreamEvent(
                        "error",
                        com.insuranceai.chat.dto.ErrorEvent(
                            code = "CHAT:LLM_RESPONSE_FAILED",
                            message = e.message ?: "AI 답변 생성에 실패했습니다",
                        ),
                    ),
                )
                sink.tryEmitComplete()
            }
        }

        return sink.asFlux()
    }

    /** LLM으로 세션 제목 생성 */
    private suspend fun generateTitle(message: String): String =
        try {
            suspendingChatClient
                .call(
                    userMessage = "다음 메시지를 10자 이내로 요약해주세요. 요약만 출력하세요:\n$message",
                    systemMessage = "간단하고 명확한 제목을 생성해주세요. 부연 설명 없이 제목만 출력하세요.",
                ).take(30)
        } catch (e: Exception) {
            log.warn("LLM 제목 생성 실패, 메시지 앞 30자로 폴백", e)
            message.take(30)
        }
}
