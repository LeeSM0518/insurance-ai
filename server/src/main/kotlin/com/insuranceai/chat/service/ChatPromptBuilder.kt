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
- 관련 내용이 조금이라도 있다면 해당 내용을 안내하고, 한계를 명시하세요.
- 관련 내용이 전혀 없는 경우에만 "제공된 약관 자료에서 정확한 답변을 드리기 어렵습니다"라고 안내하세요.
"""

        const val MEDIUM_QUALITY_HINT =
            """
참고: 검색된 약관 자료가 질문과 부분적으로 관련될 수 있습니다.
- 제공된 자료에서 질문과 관련된 내용을 최대한 찾아 답변하세요.
- 직접적으로 일치하는 내용이 아니더라도, 관련된 조문의 내용을 설명하세요.
- 답변에서 다루지 못하는 부분은 명시하세요.
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

        // 검색 품질에 따른 힌트 추가
        when (context.searchQuality) {
            SearchQuality.LOW, SearchQuality.NO_RESULTS -> {
                systemPrompt = "$systemPrompt\n\n$LOW_QUALITY_WARNING"
            }
            SearchQuality.MEDIUM -> {
                systemPrompt = "$systemPrompt\n\n$MEDIUM_QUALITY_HINT"
            }
            else -> { /* HIGH: 추가 힌트 없음 */ }
        }

        val userPrompt = "${context.contextText}\n\n## 사용자 질문\n$userMessage"

        return ChatPrompt(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }
}
