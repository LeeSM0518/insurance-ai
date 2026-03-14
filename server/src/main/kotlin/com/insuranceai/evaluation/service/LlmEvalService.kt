package com.insuranceai.evaluation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/** LLM 평가 응답 파싱용 DTO */
data class LlmJudgeResponse(
    val faithfulness: BigDecimal,
    val accuracy: BigDecimal,
    val completeness: BigDecimal,
    val source_citation: BigDecimal,
    val hallucination_detected: Boolean,
    val hallucination_details: String?,
    val reasoning: String?,
)

/** LLM 평가 결과 */
data class LlmEvalResult(
    val faithfulnessScore: BigDecimal,
    val accuracyScore: BigDecimal,
    val completenessScore: BigDecimal,
    val sourceCitationScore: BigDecimal,
    val weightedTotal: BigDecimal,
    val hallucinationDetected: Boolean,
    val hallucinationDetails: String?,
    val reasoning: String?,
)

@Service
class LlmEvalService(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 가중치: 정확성 40%, 키워드 정확도 25%, 완전성 25%, 출처 인용 10% */
    private val weights =
        mapOf(
            "faithfulness" to BigDecimal("0.40"),
            "accuracy" to BigDecimal("0.25"),
            "completeness" to BigDecimal("0.25"),
            "source_citation" to BigDecimal("0.10"),
        )

    /** LLM을 활용한 답변 품질 평가 */
    suspend fun evaluate(
        qaPair: GoldenQaPairEntity,
        actualAnswer: String,
    ): LlmEvalResult {
        val prompt =
            promptLoader.render(
                "evaluation/llm-judge.st",
                mapOf(
                    "question" to qaPair.question,
                    "expectedAnswer" to qaPair.expectedAnswer,
                    "actualAnswer" to actualAnswer,
                    "mustInclude" to qaPair.mustInclude.joinToString(", "),
                    "mustNotInclude" to qaPair.mustNotInclude.joinToString(", "),
                ),
            )

        val response = suspendingChatClient.call(userMessage = prompt)
        val parsed = parseResponse(response, qaPair.qaCode)

        val weightedTotal =
            parsed.faithfulness
                .multiply(weights["faithfulness"])
                .add(parsed.accuracy.multiply(weights["accuracy"]))
                .add(parsed.completeness.multiply(weights["completeness"]))
                .add(parsed.source_citation.multiply(weights["source_citation"]))
                .setScale(4, RoundingMode.HALF_UP)

        return LlmEvalResult(
            faithfulnessScore = parsed.faithfulness,
            accuracyScore = parsed.accuracy,
            completenessScore = parsed.completeness,
            sourceCitationScore = parsed.source_citation,
            weightedTotal = weightedTotal,
            hallucinationDetected = parsed.hallucination_detected,
            hallucinationDetails = parsed.hallucination_details,
            reasoning = parsed.reasoning,
        )
    }

    /** LLM 응답 JSON 파싱 */
    private fun parseResponse(
        response: String,
        qaCode: String,
    ): LlmJudgeResponse {
        val jsonStr =
            response
                .substringAfter("{")
                .substringBeforeLast("}")
                .let { "{$it}" }

        return try {
            objectMapper.readValue<LlmJudgeResponse>(jsonStr)
        } catch (e: Exception) {
            log.error("LLM 평가 응답 파싱 실패 ({}): {}", qaCode, e.message)
            LlmJudgeResponse(
                faithfulness = BigDecimal.ZERO,
                accuracy = BigDecimal.ZERO,
                completeness = BigDecimal.ZERO,
                source_citation = BigDecimal.ZERO,
                hallucination_detected = false,
                hallucination_details = "파싱 실패: ${e.message}",
                reasoning = null,
            )
        }
    }
}
