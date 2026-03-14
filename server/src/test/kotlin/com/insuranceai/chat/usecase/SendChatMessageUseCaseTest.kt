package com.insuranceai.chat.usecase

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.DoneEvent
import com.insuranceai.chat.dto.ErrorEvent
import com.insuranceai.chat.dto.GeneratingEvent
import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.chat.dto.TokenEvent
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.chat.entity.ChatSessionEntity
import com.insuranceai.chat.error.ChatError
import com.insuranceai.chat.service.ChatPrompt
import com.insuranceai.chat.service.ChatPromptBuilder
import com.insuranceai.chat.service.ChatService
import com.insuranceai.chat.service.ContextBuilder
import com.insuranceai.chat.service.ContextResult
import com.insuranceai.chat.service.QuestionAnalyzer
import com.insuranceai.chat.service.StreamingService
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.service.SourceVerificationService
import com.insuranceai.search.dto.FusedSearchResult
import com.insuranceai.search.service.SearchService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendChatMessageUseCaseTest {
    private val chatService = mockk<ChatService>(relaxed = true)
    private val questionAnalyzer = mockk<QuestionAnalyzer>()
    private val searchService = mockk<SearchService>()
    private val contextBuilder = mockk<ContextBuilder>()
    private val chatPromptBuilder = mockk<ChatPromptBuilder>()
    private val streamingService = mockk<StreamingService>()
    private val suspendingChatClient = mockk<SuspendingChatClient>()
    private val sourceVerificationService = mockk<SourceVerificationService>(relaxed = true)

    private val useCase =
        SendChatMessageUseCase(
            chatService,
            questionAnalyzer,
            searchService,
            contextBuilder,
            chatPromptBuilder,
            streamingService,
            suspendingChatClient,
            sourceVerificationService,
        )

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val message = "삼성생명 종신보험 면책사항 알려줘"

    private val defaultAnalysis =
        QuestionAnalysis(
            companies = listOf("삼성생명"),
            categories = emptyList(),
            contentTypes = listOf("EXEMPTION"),
            isComparison = false,
            needsSupplementary = SupplementaryNeeds(),
        )

    private val articleId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private val defaultSearchResult =
        FusedSearchResult(
            articleId = articleId,
            productId = productId,
            productName = "종신보험",
            companyId = UUID.randomUUID(),
            companyName = "삼성생명",
            insuranceType = "LIFE",
            contentType = "EXEMPTION",
            articleNumber = "제5조",
            articleTitle = "면책사항",
            content = "면책 내용",
            dataSource = "ASSOCIATION",
            relevanceScore = 0.85f,
            keywordRank = 1,
            vectorRank = 2,
        )

    private val defaultContextResult =
        ContextResult(
            contextText = "## 검색된 약관 자료\n면책 내용",
            sources =
                listOf(
                    SourceInfo(
                        articleId = articleId,
                        insuranceCompany = "삼성생명",
                        productId = productId,
                        productName = "종신보험",
                        contentType = "EXEMPTION",
                        articleNumber = "제5조",
                        articleTitle = "면책사항",
                        relevanceScore = 0.85f,
                        dataSource = "ASSOCIATION",
                    ),
                ),
            tokenEstimate = 100,
        )

    private val defaultChatPrompt =
        ChatPrompt(
            systemPrompt = "시스템 프롬프트",
            userPrompt = "컨텍스트\n\n## 사용자 질문\n$message",
        )

    private fun setupDefaultMocks() {
        coEvery { chatService.validateSessionOwner(sessionId, userId) } returns
            ChatSessionEntity(id = sessionId, userId = userId)
        coEvery { chatService.saveUserMessage(sessionId, message) } returns
            ChatMessageEntity(
                id = UUID.randomUUID(),
                chatSessionId = sessionId,
                role = "USER",
                content = message,
            )
        coEvery { questionAnalyzer.analyze(message) } returns defaultAnalysis
        coEvery { searchService.searchForRag(message, defaultAnalysis) } returns listOf(defaultSearchResult)
        coEvery { chatService.getRecentMessages(sessionId) } returns emptyList()
        coEvery { chatService.isFirstUserMessage(sessionId) } returns false
        coEvery { contextBuilder.build(any(), any(), any()) } returns defaultContextResult
        every { chatPromptBuilder.build(message, defaultContextResult, false) } returns defaultChatPrompt
    }

    @Test
    fun `전체 파이프라인이 정상적으로 실행된다`() {
        setupDefaultMocks()

        val messageId = UUID.randomUUID()
        every { streamingService.stream(defaultChatPrompt, any(), any(), any()) } returns
            Flux.just(
                ChatStreamEvent("generating", GeneratingEvent("응답 생성 중...")),
                ChatStreamEvent("token", TokenEvent("면책")),
                ChatStreamEvent("done", DoneEvent(messageId = messageId, suggestedQuestions = emptyList())),
            )

        val flux = useCase.execute(sessionId, message, userId)

        StepVerifier
            .create(flux)
            .assertNext { assertEquals("searching", it.type) }
            .assertNext { assertEquals("sources_found", it.type) }
            .assertNext { assertEquals("generating", it.type) }
            .assertNext { assertEquals("token", it.type) }
            .assertNext { assertEquals("done", it.type) }
            .verifyComplete()
    }

    @Test
    fun `세션이 존재하지 않으면 에러 이벤트가 발행된다`() {
        coEvery { chatService.validateSessionOwner(sessionId, userId) } throws ChatError.SessionNotFound

        val flux = useCase.execute(sessionId, message, userId)

        StepVerifier
            .create(flux)
            .assertNext { event ->
                assertEquals("error", event.type)
                assertTrue(event.data is ErrorEvent)
            }.verifyComplete()
    }

    @Test
    fun `세션 소유자 불일치 시 에러 이벤트가 발행된다`() {
        coEvery { chatService.validateSessionOwner(sessionId, userId) } throws ChatError.NotOwner

        val flux = useCase.execute(sessionId, message, userId)

        StepVerifier
            .create(flux)
            .assertNext { event ->
                assertEquals("error", event.type)
                val error = event.data as ErrorEvent
                assertTrue(error.message.isNotBlank())
            }.verifyComplete()
    }

    @Test
    fun `검색 결과가 없어도 정상 처리된다`() {
        setupDefaultMocks()
        coEvery { searchService.searchForRag(message, defaultAnalysis) } returns emptyList()
        coEvery { contextBuilder.build(emptyList(), any(), any()) } returns
            ContextResult(contextText = "", sources = emptyList(), tokenEstimate = 0)
        every { chatPromptBuilder.build(message, any(), false) } returns defaultChatPrompt

        val messageId = UUID.randomUUID()
        every { streamingService.stream(any(), any(), any(), any()) } returns
            Flux.just(
                ChatStreamEvent("generating", GeneratingEvent("응답 생성 중...")),
                ChatStreamEvent("done", DoneEvent(messageId = messageId, suggestedQuestions = emptyList())),
            )

        val flux = useCase.execute(sessionId, message, userId)

        StepVerifier
            .create(flux)
            .assertNext { assertEquals("searching", it.type) }
            .assertNext { event ->
                assertEquals("sources_found", event.type)
            }.assertNext { assertEquals("generating", it.type) }
            .assertNext { assertEquals("done", it.type) }
            .verifyComplete()
    }
}
