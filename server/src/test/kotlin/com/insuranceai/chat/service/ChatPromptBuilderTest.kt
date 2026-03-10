package com.insuranceai.chat.service

import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.common.ai.PromptLoader
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatPromptBuilderTest {
    private val promptLoader = mockk<PromptLoader>()
    private val builder = ChatPromptBuilder(promptLoader)

    init {
        every { promptLoader.load("chat/system-prompt.st") } returns "당신은 보험 설계사를 위한 약관 검색·분석 AI 어시스턴트입니다."
        every { promptLoader.load("chat/comparison-hint.st") } returns "[비교 질의 감지] 사용자가 보험사 간 비교를 요청했습니다."
    }

    private val context =
        ContextResult(
            contextText = "## 검색된 약관 자료\n\n### [1] 삼성생명 - 테스트보험\n내용",
            sources =
                listOf(
                    SourceInfo(
                        articleId = UUID.randomUUID(),
                        insuranceCompany = "삼성생명",
                        productId = UUID.randomUUID(),
                        productName = "테스트 보험",
                        contentType = "COVERAGE",
                        articleNumber = "제1조",
                        articleTitle = "보장내용",
                        relevanceScore = 0.8f,
                        dataSource = "ASSOCIATION",
                    ),
                ),
            tokenEstimate = 100,
        )

    @Test
    fun `기본 프롬프트 조합`() {
        val prompt = builder.build("면책사항 알려주세요", context, isComparison = false)

        assertTrue(prompt.systemPrompt.contains("약관 검색·분석 AI 어시스턴트"))
        assertFalse(prompt.systemPrompt.contains("비교 질의 감지"))
        assertTrue(prompt.userPrompt.contains("## 검색된 약관 자료"))
        assertTrue(prompt.userPrompt.contains("## 사용자 질문"))
        assertTrue(prompt.userPrompt.contains("면책사항 알려주세요"))
    }

    @Test
    fun `비교 질의 시 비교 힌트가 추가된다`() {
        val prompt = builder.build("암보험 비교해주세요", context, isComparison = true)

        assertTrue(prompt.systemPrompt.contains("약관 검색·분석 AI 어시스턴트"))
        assertTrue(prompt.systemPrompt.contains("비교 질의 감지"))
    }

    @Test
    fun `사용자 프롬프트에 컨텍스트와 질문이 포함된다`() {
        val prompt = builder.build("보장 범위 알려주세요", context, isComparison = false)

        assertTrue(prompt.userPrompt.startsWith("## 검색된 약관 자료"))
        assertTrue(prompt.userPrompt.contains("## 사용자 질문\n보장 범위 알려주세요"))
    }

    @Test
    fun `검색 품질이 LOW이면 경고 프롬프트가 추가된다`() {
        val lowQualityContext = context.copy(searchQuality = SearchQuality.LOW)
        val prompt = builder.build("테스트 질문", lowQualityContext, isComparison = false)

        assertTrue(prompt.systemPrompt.contains("검색된 약관 자료의 관련도가 낮습니다"))
    }

    @Test
    fun `검색 품질이 NO_RESULTS이면 경고 프롬프트가 추가된다`() {
        val noResultsContext = context.copy(searchQuality = SearchQuality.NO_RESULTS)
        val prompt = builder.build("테스트 질문", noResultsContext, isComparison = false)

        assertTrue(prompt.systemPrompt.contains("검색된 약관 자료의 관련도가 낮습니다"))
    }

    @Test
    fun `검색 품질이 HIGH이면 경고 프롬프트가 추가되지 않는다`() {
        val highQualityContext = context.copy(searchQuality = SearchQuality.HIGH)
        val prompt = builder.build("테스트 질문", highQualityContext, isComparison = false)

        assertFalse(prompt.systemPrompt.contains("검색된 약관 자료의 관련도가 낮습니다"))
    }
}
