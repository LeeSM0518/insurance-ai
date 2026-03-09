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
        every { promptLoader.load("chat/system-prompt.st") } returns "당신은 보험 약관 전문 AI 어시스턴트입니다."
        every { promptLoader.load("chat/comparison-hint.st") } returns "[비교 질의 감지] 비교표 형식으로 답변하세요."
    }

    private val context =
        ContextResult(
            contextText = "## 검색된 약관 자료\n\n### [출처 1] 삼성생명 - 테스트보험\n내용",
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

        assertTrue(prompt.systemPrompt.contains("보험 약관 전문 AI 어시스턴트"))
        assertFalse(prompt.systemPrompt.contains("비교 질의 감지"))
        assertTrue(prompt.userPrompt.contains("## 검색된 약관 자료"))
        assertTrue(prompt.userPrompt.contains("## 사용자 질문"))
        assertTrue(prompt.userPrompt.contains("면책사항 알려주세요"))
    }

    @Test
    fun `비교 질의 시 비교 힌트가 추가된다`() {
        val prompt = builder.build("암보험 비교해주세요", context, isComparison = true)

        assertTrue(prompt.systemPrompt.contains("보험 약관 전문 AI 어시스턴트"))
        assertTrue(prompt.systemPrompt.contains("비교 질의 감지"))
    }

    @Test
    fun `사용자 프롬프트에 컨텍스트와 질문이 포함된다`() {
        val prompt = builder.build("보장 범위 알려주세요", context, isComparison = false)

        assertTrue(prompt.userPrompt.startsWith("## 검색된 약관 자료"))
        assertTrue(prompt.userPrompt.contains("## 사용자 질문\n보장 범위 알려주세요"))
    }
}
