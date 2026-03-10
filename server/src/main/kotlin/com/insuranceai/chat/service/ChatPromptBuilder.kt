package com.insuranceai.chat.service

import com.insuranceai.common.ai.PromptLoader
import org.springframework.stereotype.Service

data class ChatPrompt(
    val systemPrompt: String,
    val userPrompt: String,
)

@Service
class ChatPromptBuilder(
    private val promptLoader: PromptLoader,
) {
    companion object {
        const val LOW_QUALITY_WARNING =
            """
주의: 검색된 약관 자료의 관련도가 낮습니다.
- 제공된 자료에서 직접적인 답을 찾기 어려울 수 있습니다.
- 자료에 근거하지 않은 추측은 하지 마세요.
- "제공된 약관 자료에서 정확한 답변을 드리기 어렵습니다"라고 안내하세요.
"""
    }

    /** 최종 프롬프트 조합 */
    fun build(
        userMessage: String,
        context: ContextResult,
        isComparison: Boolean,
    ): ChatPrompt {
        var systemPrompt = promptLoader.load("chat/system-prompt.st")

        if (isComparison) {
            val comparisonHint = promptLoader.load("chat/comparison-hint.st")
            systemPrompt = "$systemPrompt\n\n$comparisonHint"
        }

        // 검색 품질이 낮으면 경고 프롬프트 추가
        if (context.searchQuality == SearchQuality.LOW || context.searchQuality == SearchQuality.NO_RESULTS) {
            systemPrompt = "$systemPrompt\n\n$LOW_QUALITY_WARNING"
        }

        val userPrompt = "${context.contextText}\n\n## 사용자 질문\n$userMessage"

        return ChatPrompt(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }
}
