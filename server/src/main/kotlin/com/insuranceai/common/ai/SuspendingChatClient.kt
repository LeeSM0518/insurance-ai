package com.insuranceai.common.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.ai.chat.client.ChatClient
import reactor.core.publisher.Flux

/**
 * Spring AI ChatClient를 코루틴 환경에서 사용하기 위한 래퍼.
 * blocking 호출은 Dispatchers.IO로 전환하고, 스트리밍은 Flux를 그대로 반환합니다.
 */
class SuspendingChatClient(
    private val chatClient: ChatClient,
) {
    /** 단일 응답 호출 (코루틴) */
    suspend fun call(
        userMessage: String,
        systemMessage: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            chatClient
                .prompt()
                .apply { systemMessage?.let { system(it) } }
                .user(userMessage)
                .call()
                .content() ?: ""
        }

    /** 구조화된 응답 호출 (코루틴) - JSON을 지정 타입으로 역직렬화 */
    suspend fun <T : Any> callEntity(
        userMessage: String,
        systemMessage: String? = null,
        responseType: Class<T>,
    ): T =
        withContext(Dispatchers.IO) {
            chatClient
                .prompt()
                .apply { systemMessage?.let { system(it) } }
                .user(userMessage)
                .call()
                .entity(responseType)!!
        }

    /** 스트리밍 응답 (Flux 반환) */
    fun stream(
        userMessage: String,
        systemMessage: String? = null,
    ): Flux<String> =
        chatClient
            .prompt()
            .apply { systemMessage?.let { system(it) } }
            .user(userMessage)
            .stream()
            .content()
}
