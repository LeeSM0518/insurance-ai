package com.insuranceai.chat.service

import com.insuranceai.chat.dto.DoneEvent
import com.insuranceai.chat.dto.ErrorEvent
import com.insuranceai.chat.dto.GeneratingEvent
import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.chat.dto.SuggestedQuestions
import com.insuranceai.chat.dto.TokenEvent
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingServiceTest {
    private val suspendingChatClient = mockk<SuspendingChatClient>()
    private val promptLoader = mockk<PromptLoader>()
    private val service = StreamingService(suspendingChatClient, promptLoader)

    private val chatPrompt =
        ChatPrompt(
            systemPrompt = "시스템 프롬프트",
            userPrompt = "사용자 질문",
        )
    private val sources =
        listOf(
            SourceInfo(
                articleId = UUID.randomUUID(),
                insuranceCompany = "삼성생명",
                productId = UUID.randomUUID(),
                productName = "종신보험",
                articleNumber = "제1조",
                articleTitle = "보장내용",
                relevanceScore = 0.9f,
                dataSource = "ASSOCIATION",
            ),
        )

    @Test
    fun `정상 흐름 - generating, token, done 이벤트가 순서대로 발행된다`() {
        every { suspendingChatClient.stream("사용자 질문", "시스템 프롬프트") } returns
            Flux.just("안녕", "하세요")
        every { promptLoader.load("chat/suggested-questions.st") } returns "추천 프롬프트"
        coEvery {
            suspendingChatClient.callEntity(any(), any(), eq(SuggestedQuestions::class.java))
        } returns SuggestedQuestions(questions = listOf("추천 질문 1", "추천 질문 2"))

        var savedResponse = ""
        val flux =
            service.stream(chatPrompt, sources) { fullResponse, _ ->
                savedResponse = fullResponse
            }

        StepVerifier
            .create(flux)
            .assertNext { event ->
                assertEquals("generating", event.type)
                assertTrue(event.data is GeneratingEvent)
            }.assertNext { event ->
                assertEquals("token", event.type)
                assertEquals("안녕", (event.data as TokenEvent).content)
            }.assertNext { event ->
                assertEquals("token", event.type)
                assertEquals("하세요", (event.data as TokenEvent).content)
            }.assertNext { event ->
                assertEquals("done", event.type)
                val done = event.data as DoneEvent
                assertEquals(listOf("추천 질문 1", "추천 질문 2"), done.suggestedQuestions)
            }.verifyComplete()

        assertEquals("안녕하세요", savedResponse)
    }

    @Test
    fun `추천 질문 생성 실패 시 빈 리스트로 폴백된다`() {
        every { suspendingChatClient.stream("사용자 질문", "시스템 프롬프트") } returns
            Flux.just("응답")
        every { promptLoader.load("chat/suggested-questions.st") } returns "추천 프롬프트"
        coEvery {
            suspendingChatClient.callEntity(any(), any(), eq(SuggestedQuestions::class.java))
        } throws RuntimeException("LLM 에러")

        val flux = service.stream(chatPrompt, sources) { _, _ -> }

        StepVerifier
            .create(flux)
            .assertNext { assertEquals("generating", it.type) }
            .assertNext { assertEquals("token", it.type) }
            .assertNext { event ->
                assertEquals("done", event.type)
                val done = event.data as DoneEvent
                assertTrue(done.suggestedQuestions.isEmpty())
            }.verifyComplete()
    }

    @Test
    fun `LLM 스트리밍 에러 시 error 이벤트가 발행된다`() {
        every { suspendingChatClient.stream("사용자 질문", "시스템 프롬프트") } returns
            Flux.error(RuntimeException("LLM 장애"))

        val flux = service.stream(chatPrompt, sources) { _, _ -> }

        StepVerifier
            .create(flux)
            .assertNext { assertEquals("generating", it.type) }
            .assertNext { event ->
                assertEquals("error", event.type)
                val error = event.data as ErrorEvent
                assertEquals("CHAT:LLM_RESPONSE_FAILED", error.code)
            }.verifyComplete()
    }
}
