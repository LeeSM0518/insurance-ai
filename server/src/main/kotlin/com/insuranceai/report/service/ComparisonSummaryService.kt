package com.insuranceai.report.service

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.ErrorEvent
import com.insuranceai.chat.service.ChatPrompt
import com.insuranceai.chat.service.StreamingService
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.report.dto.ComparisonRequest
import com.insuranceai.report.dto.ComparisonResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

@Service
class ComparisonSummaryService(
    private val comparisonService: ComparisonService,
    private val promptLoader: PromptLoader,
    private val streamingService: StreamingService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 비교 데이터를 조회한 후 AI 요약 생성 (SSE)
     */
    fun generateSummary(
        request: ComparisonRequest,
        userId: UUID,
    ): Flux<ChatStreamEvent> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<ChatStreamEvent>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 비교 데이터 조회
                val comparisonData = comparisonService.compareProducts(request)

                // 2. 프롬프트 구성
                val systemPrompt = promptLoader.load("report/comparison-summary.st")
                val userPrompt = formatComparisonForLlm(comparisonData)
                val chatPrompt = ChatPrompt(systemPrompt, userPrompt)

                // 3. StreamingService로 스트리밍
                streamingService
                    .stream(
                        chatPrompt = chatPrompt,
                        sources = emptyList(),
                        onComplete = { fullResponse, _, _ ->
                            logger.info("비교 요약 생성 완료: ${request.category}, ${fullResponse.length}자")
                        },
                    ).doOnNext { sink.tryEmitNext(it) }
                    .doOnComplete { sink.tryEmitComplete() }
                    .subscribe()
            } catch (e: Exception) {
                logger.error("비교 요약 생성 오류", e)
                sink.tryEmitNext(
                    ChatStreamEvent(
                        "error",
                        ErrorEvent("COMPARISON:SUMMARY_FAILED", "비교 요약 생성에 실패했습니다"),
                    ),
                )
                sink.tryEmitComplete()
            }
        }

        return sink.asFlux()
    }

    private fun formatComparisonForLlm(data: ComparisonResponse): String {
        val sb = StringBuilder()
        sb.appendLine("## 비교 대상 상품 데이터")
        sb.appendLine()
        data.companies.forEach { company ->
            company.products.forEach { product ->
                sb.appendLine("### ${company.companyName} - ${product.productName}")
                sb.appendLine("갱신유형: ${product.renewalType ?: "미확인"}")
                sb.appendLine("보장기간: ${product.insurancePeriod ?: "미확인"}")
                sb.appendLine()
                sb.appendLine("**보장 내용:**")
                product.coverages.forEach { c ->
                    sb.appendLine("- ${c.name}: ${c.amount ?: "미확인"} (${c.conditions ?: ""}) [${c.articleRef ?: ""}]")
                }
                sb.appendLine()
                sb.appendLine("**면책/감액:**")
                product.exemptions.forEach { e ->
                    sb.appendLine("- ${e.name}: ${e.description ?: ""} (${e.period ?: ""})")
                }
                product.premiumExample?.let { p ->
                    sb.appendLine()
                    sb.appendLine("**보험료 예시:** ${p.age}세/${p.gender} 월 ${p.monthlyPremium}원")
                }
                sb.appendLine()
            }
        }
        return sb.toString()
    }
}
