package com.insuranceai.common.config

import com.insuranceai.common.ai.PromptLoader
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertNotNull

class SpringAiConfigTest {
    @Test
    fun `ChatClient 빈 생성 확인`() {
        val promptLoader = mockk<PromptLoader>()
        every { promptLoader.load("chat/system-prompt.st") } returns "시스템 프롬프트"

        val builder = mockk<ChatClient.Builder>(relaxed = true)
        val chatClient = mockk<ChatClient>()
        every { builder.defaultSystem(any<String>()) } returns builder
        every { builder.build() } returns chatClient

        val config = SpringAiConfig(promptLoader)
        val result = config.chatClient(builder)
        assertNotNull(result)
    }

    @Test
    fun `SuspendingChatClient 빈 생성 확인`() {
        val promptLoader = mockk<PromptLoader>()
        every { promptLoader.load("chat/system-prompt.st") } returns "시스템 프롬프트"

        val chatClient = mockk<ChatClient>()
        val config = SpringAiConfig(promptLoader)
        val result = config.suspendingChatClient(chatClient)
        assertNotNull(result)
    }
}
