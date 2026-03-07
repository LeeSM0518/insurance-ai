package com.insuranceai.common.config

import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring AI 관련 빈 설정.
 * ChatClient에 시스템 프롬프트를 기본 설정하고, 코루틴 래퍼를 등록합니다.
 */
@Configuration
class SpringAiConfig(
    private val promptLoader: PromptLoader,
) {
    @Bean
    fun chatClient(builder: ChatClient.Builder): ChatClient =
        builder
            .defaultSystem(promptLoader.load("chat/system-prompt.st"))
            .build()

    @Bean
    fun suspendingChatClient(chatClient: ChatClient): SuspendingChatClient = SuspendingChatClient(chatClient)
}
