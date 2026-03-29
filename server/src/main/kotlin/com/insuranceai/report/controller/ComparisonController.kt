package com.insuranceai.report.controller

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.report.dto.ComparisonCategoryInfo
import com.insuranceai.report.dto.ComparisonRequest
import com.insuranceai.report.dto.ComparisonResponse
import com.insuranceai.report.service.ComparisonService
import com.insuranceai.report.service.ComparisonSummaryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/api/v1/compare")
@Tag(name = "Comparison", description = "보험 상품 비교 분석 API")
@SecurityRequirement(name = "bearerAuth")
class ComparisonController(
    private val comparisonService: ComparisonService,
    private val comparisonSummaryService: ComparisonSummaryService,
) {
    @PostMapping
    @Operation(summary = "보험사별 상품 비교 데이터 조회")
    suspend fun compareProducts(
        @RequestBody @Valid request: ComparisonRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<ComparisonResponse> {
        val result = comparisonService.compareProducts(request)
        return ServiceResponse.ok(result)
    }

    @PostMapping("/summary", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "비교 결과 AI 요약 생성 (SSE)")
    fun generateSummary(
        @RequestBody @Valid request: ComparisonRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): Flux<ServerSentEvent<ChatStreamEvent>> =
        comparisonSummaryService
            .generateSummary(request, principal.userId)
            .map { event -> ServerSentEvent.builder(event).build() }

    @GetMapping("/categories")
    @Operation(summary = "비교 가능한 카테고리 목록 조회")
    suspend fun getCategories(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<List<ComparisonCategoryInfo>> {
        val categories = comparisonService.getAvailableCategories()
        return ServiceResponse.ok(categories)
    }
}
