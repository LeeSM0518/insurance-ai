package com.insuranceai.report.service

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.ErrorEvent
import com.insuranceai.chat.service.ChatPrompt
import com.insuranceai.chat.service.StreamingService
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.report.dto.ComparisonRequest
import com.insuranceai.report.dto.ComparisonResponse
import com.insuranceai.report.dto.ProductComparison
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

    companion object {
        private const val MAX_PRODUCTS_PER_COMPANY = 5
        private const val MAX_COVERAGES_PER_PRODUCT = 10
        private const val MAX_EXEMPTIONS_PER_PRODUCT = 5
        private const val MAX_EXEMPTION_DESC_LENGTH = 50
    }

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

                // 2. LLM용 대표 상품 선별
                val filteredData = selectRepresentativeProducts(comparisonData)
                val totalProducts = comparisonData.companies.sumOf { it.products.size }
                val selectedProducts = filteredData.companies.sumOf { it.products.size }
                logger.info("LLM 상품 선별: ${totalProducts}개 → ${selectedProducts}개")

                // 3. 프롬프트 구성
                val systemPrompt = promptLoader.load("report/comparison-summary.st")
                val userPrompt = formatComparisonForLlm(filteredData)
                val chatPrompt = ChatPrompt(systemPrompt, userPrompt)

                // 4. StreamingService로 스트리밍
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

    /** 보험사당 대표 상품 선별 */
    private fun selectRepresentativeProducts(
        data: ComparisonResponse,
        maxPerCompany: Int = MAX_PRODUCTS_PER_COMPANY,
    ): ComparisonResponse {
        val filteredCompanies =
            data.companies.map { company ->
                val sorted =
                    company.products.sortedWith(
                        compareByDescending<ProductComparison> { it.coverages.size }
                            .thenByDescending { if (it.premiumExample != null) 1 else 0 }
                            .thenByDescending { if (it.surrenderValueSample != null) 1 else 0 },
                    )
                company.copy(products = sorted.take(maxPerCompany))
            }
        return data.copy(companies = filteredCompanies)
    }

    /** LLM용 압축 포맷 (conditions 제거, 보장/면책 수 제한) */
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

                // 보장 내용: 최대 10개
                sb.appendLine("**보장 내용:**")
                val coverages = product.coverages
                coverages.take(MAX_COVERAGES_PER_PRODUCT).forEach { c ->
                    sb.appendLine("- ${c.name}: ${c.amount ?: "미확인"} [${c.articleRef ?: ""}]")
                }
                if (coverages.size > MAX_COVERAGES_PER_PRODUCT) {
                    sb.appendLine("- 외 ${coverages.size - MAX_COVERAGES_PER_PRODUCT}건")
                }
                sb.appendLine()

                // 면책/감액: 최대 5개, 50자
                sb.appendLine("**면책/감액:**")
                val exemptions = product.exemptions
                exemptions.take(MAX_EXEMPTIONS_PER_PRODUCT).forEach { e ->
                    val desc = e.description?.take(MAX_EXEMPTION_DESC_LENGTH) ?: ""
                    val period = e.period?.let { " ($it)" } ?: ""
                    sb.appendLine("- ${e.name}: ${desc}$period")
                }
                if (exemptions.size > MAX_EXEMPTIONS_PER_PRODUCT) {
                    sb.appendLine("- 외 ${exemptions.size - MAX_EXEMPTIONS_PER_PRODUCT}건")
                }

                // 보험료 예시
                product.premiumExample?.let { p ->
                    sb.appendLine()
                    sb.appendLine("**보험료:** ${p.age}세/${p.gender} 월 ${p.monthlyPremium}원 (보험금액 ${p.insuranceAmountText ?: "미확인"})")
                }

                // 해약환급금
                product.surrenderValueSample?.let { s ->
                    sb.appendLine("**해약환급금(${s.elapsedMonths}개월):** ${s.surrenderRate}%")
                }

                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
