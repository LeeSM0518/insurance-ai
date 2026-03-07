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

        val userPrompt = "${context.contextText}\n\n## 사용자 질문\n$userMessage"

        return ChatPrompt(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }
}
