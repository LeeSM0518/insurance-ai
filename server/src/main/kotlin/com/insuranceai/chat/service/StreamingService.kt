package com.insuranceai.chat.service

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.DoneEvent
import com.insuranceai.chat.dto.ErrorEvent
import com.insuranceai.chat.dto.GeneratingEvent
import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.chat.dto.SuggestedQuestions
import com.insuranceai.chat.dto.TokenEvent
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

@Service
class StreamingService(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** LLM 스트리밍 → ChatStreamEvent Flux 변환 */
    fun stream(
        chatPrompt: ChatPrompt,
        sources: List<SourceInfo>,
        onComplete: suspend (fullResponse: String, messageId: UUID) -> Unit,
    ): Flux<ChatStreamEvent> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<ChatStreamEvent>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // generating 이벤트
                sink.tryEmitNext(ChatStreamEvent("generating", GeneratingEvent("응답 생성 중...")))

                // LLM 스트리밍
                val fullResponse = StringBuilder()
                suspendingChatClient
                    .stream(chatPrompt.userPrompt, chatPrompt.systemPrompt)
                    .doOnNext { token ->
                        fullResponse.append(token)
                        sink.tryEmitNext(ChatStreamEvent("token", TokenEvent(token)))
                    }.doOnComplete {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val responseText = fullResponse.toString()
                                val messageId = UUID.randomUUID()

                                // 추천 질문 생성
                                val suggestedQuestions = generateSuggestedQuestions(responseText)

                                // done 이벤트
                                sink.tryEmitNext(
                                    ChatStreamEvent(
                                        "done",
                                        DoneEvent(
                                            messageId = messageId,
                                            suggestedQuestions = suggestedQuestions,
                                        ),
                                    ),
                                )

                                // 메시지 저장 콜백
                                onComplete(responseText, messageId)
                            } catch (e: Exception) {
                                log.error("스트림 완료 처리 실패", e)
                            } finally {
                                sink.tryEmitComplete()
                            }
                        }
                    }.doOnError { e ->
                        log.error("LLM 스트리밍 에러", e)
                        sink.tryEmitNext(
                            ChatStreamEvent(
                                "error",
                                ErrorEvent("CHAT:LLM_RESPONSE_FAILED", "AI 답변 생성에 실패했습니다"),
                            ),
                        )
                        sink.tryEmitComplete()
                    }.subscribe({}, { /* doOnError에서 처리 완료 */ })
            } catch (e: Exception) {
                log.error("스트리밍 시작 에러", e)
                sink.tryEmitNext(
                    ChatStreamEvent(
                        "error",
                        ErrorEvent("CHAT:LLM_RESPONSE_FAILED", "AI 답변 생성에 실패했습니다"),
                    ),
                )
                sink.tryEmitComplete()
            }
        }

        return sink.asFlux()
    }

    /** 추천 질문 생성 (실패 시 빈 리스트) */
    private suspend fun generateSuggestedQuestions(responseText: String): List<String> =
        try {
            val systemPrompt = promptLoader.load("chat/suggested-questions.st")
            val result =
                suspendingChatClient.callEntity(
                    userMessage = responseText,
                    systemMessage = systemPrompt,
                    responseType = SuggestedQuestions::class.java,
                )
            result.questions
        } catch (e: Exception) {
            log.warn("추천 질문 생성 실패, 빈 리스트로 폴백", e)
            emptyList()
        }
}
