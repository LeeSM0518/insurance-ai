package com.insuranceai.common.integration

import io.mockk.every
import io.mockk.mockk
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.EmbeddingResultMetadata
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Flux

/**
 * 통합 테스트에서 외부 AI API (Anthropic, OpenAI) 호출을 차단합니다.
 * ChatModel과 EmbeddingModel을 MockK으로 대체합니다.
 */
@TestConfiguration
class TestSpringAiConfig {
    @Bean
    @Primary
    fun testChatModel(): ChatModel {
        val mock = mockk<ChatModel>(relaxed = true)
        every { mock.call(any<Prompt>()) } returns
            ChatResponse(listOf(Generation(AssistantMessage("테스트 AI 응답입니다."))))
        every { mock.stream(any<Prompt>()) } returns
            Flux.just(ChatResponse(listOf(Generation(AssistantMessage("테스트 AI 응답입니다.")))))
        return mock
    }

    @Bean
    @Primary
    fun testEmbeddingModel(): EmbeddingModel {
        val mock = mockk<EmbeddingModel>(relaxed = true)
        val testVector = FloatArray(1536) { 0f }
        every { mock.call(any<EmbeddingRequest>()) } returns
            EmbeddingResponse(listOf(Embedding(testVector, 0, EmbeddingResultMetadata.EMPTY)))
        return mock
    }
}
