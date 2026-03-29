package com.insuranceai.report.usecase

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.ErrorEvent
import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SearchingEvent
import com.insuranceai.chat.dto.SourcesFoundEvent
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.chat.service.ChatPrompt
import com.insuranceai.chat.service.ContextBuilder
import com.insuranceai.chat.service.ContextResult
import com.insuranceai.chat.service.StreamingService
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.error.ServiceError
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.report.dto.CheatsheetRequest
import com.insuranceai.report.error.ReportError
import com.insuranceai.report.service.CheatsheetService
import com.insuranceai.search.service.SearchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

@Component
class GenerateCheatsheetUseCase(
    private val searchService: SearchService,
    private val contextBuilder: ContextBuilder,
    private val promptLoader: PromptLoader,
    private val streamingService: StreamingService,
    private val cheatsheetService: CheatsheetService,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(
        request: CheatsheetRequest,
        userId: UUID,
    ): Flux<ChatStreamEvent> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<ChatStreamEvent>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 보험사명 검증
                val companies =
                    request.companyNames.mapNotNull { name ->
                        insuranceCompanyRepository.findByNameOrAlias(name)
                    }
                if (companies.size != request.companyNames.size) throw ReportError.CompanyNotFound
                if (companies.size < 2) throw ReportError.InvalidCompanyCount

                // 2. 검색 이벤트
                sink.tryEmitNext(ChatStreamEvent("searching", SearchingEvent("약관 데이터 검색 중...")))

                // 3. 보험사별 약관 검색 (비교 모드)
                val analysis =
                    QuestionAnalysis(
                        companies = request.companyNames,
                        categories = listOf(mapCategoryToEnum(request.category)),
                        isComparison = true,
                        searchKeywords = listOf(request.category),
                        needsSupplementary =
                            SupplementaryNeeds(
                                premiumExample = true,
                                surrenderValue = true,
                            ),
                    )
                val searchQuery = "${request.companyNames.joinToString(" ")} ${request.category} 보장 비교"
                val searchResults = searchService.searchForRag(searchQuery, analysis)

                // 4. 컨텍스트 구성
                val contextResult =
                    contextBuilder.build(
                        searchResults = searchResults,
                        analysis = analysis,
                        conversationHistory = emptyList(),
                    )

                sink.tryEmitNext(ChatStreamEvent("sources_found", SourcesFoundEvent(contextResult.sources)))

                // 5. 치트시트 전용 프롬프트 구성
                val systemPrompt = promptLoader.load("report/cheatsheet-generation.st")
                val userPrompt = buildUserPrompt(request, contextResult)
                val chatPrompt = ChatPrompt(systemPrompt, userPrompt)

                // 6. LLM 스트리밍 생성
                streamingService
                    .stream(
                        chatPrompt = chatPrompt,
                        sources = contextResult.sources,
                        titleGenerator = null,
                        onComplete = { fullResponse, _, _ ->
                            // 7. 결과 저장
                            val saved =
                                cheatsheetService.saveCheatsheet(
                                    userId = userId,
                                    content = fullResponse,
                                    request = request,
                                )
                            log.info("치트시트 저장 완료: reportId=${saved.id}")
                        },
                    ).doOnNext { event -> sink.tryEmitNext(event) }
                    .doOnComplete { sink.tryEmitComplete() }
                    .doOnError { e ->
                        log.error("치트시트 생성 오류", e)
                        sink.tryEmitNext(
                            ChatStreamEvent(
                                "error",
                                ErrorEvent("REPORT:GENERATION_FAILED", "치트시트 생성에 실패했습니다"),
                            ),
                        )
                        sink.tryEmitComplete()
                    }.subscribe()
            } catch (e: ServiceError) {
                sink.tryEmitNext(ChatStreamEvent("error", ErrorEvent(e.code, e.message)))
                sink.tryEmitComplete()
            } catch (e: Exception) {
                log.error("치트시트 생성 예외", e)
                sink.tryEmitNext(
                    ChatStreamEvent(
                        "error",
                        ErrorEvent("REPORT:UNEXPECTED_ERROR", "예기치 않은 오류가 발생했습니다"),
                    ),
                )
                sink.tryEmitComplete()
            }
        }

        return sink.asFlux()
    }

    private fun buildUserPrompt(
        request: CheatsheetRequest,
        context: ContextResult,
    ): String {
        val sb = StringBuilder()
        sb.appendLine(context.contextText)
        sb.appendLine()
        sb.appendLine("## 치트시트 생성 요청")
        sb.appendLine("- 보험 유형: ${request.category}")
        sb.appendLine("- 비교 보험사: ${request.companyNames.joinToString(", ")}")
        request.recommendedProduct?.let { sb.appendLine("- 추천 상품: $it") }
        request.customerAge?.let { sb.appendLine("- 고객 나이: ${it}세") }
        request.customerGender?.let { sb.appendLine("- 고객 성별: ${if (it == "MALE") "남성" else "여성"}") }
        request.focusAreas?.let { sb.appendLine("- 강조 영역: ${it.joinToString(", ")}") }
        return sb.toString()
    }

    private fun mapCategoryToEnum(category: String): String =
        when {
            category.contains("암") -> "CANCER"
            category.contains("실손") -> "NL_HEALTH"
            category.contains("종신") -> "WHOLE_LIFE"
            category.contains("건강") -> "DISEASE"
            category.contains("어린이") -> "CHILD"
            category.contains("연금") -> "ANNUITY"
            else -> category
        }
}
