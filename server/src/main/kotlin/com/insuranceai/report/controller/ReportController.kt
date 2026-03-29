package com.insuranceai.report.controller

import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.report.dto.CheatsheetRequest
import com.insuranceai.report.dto.ReportResponse
import com.insuranceai.report.dto.ReportSummaryResponse
import com.insuranceai.report.service.CheatsheetService
import com.insuranceai.report.usecase.GenerateCheatsheetUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Report", description = "리포트 생성/조회/공유 API")
@SecurityRequirement(name = "bearerAuth")
class ReportController(
    private val generateCheatsheetUseCase: GenerateCheatsheetUseCase,
    private val cheatsheetService: CheatsheetService,
) {
    @PostMapping("/cheatsheet/generate", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "치트시트 생성 (SSE 스트리밍)")
    fun generateCheatsheet(
        @RequestBody @Valid request: CheatsheetRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): Flux<ServerSentEvent<ChatStreamEvent>> =
        generateCheatsheetUseCase
            .execute(request, principal.userId)
            .map { event -> ServerSentEvent.builder(event).build() }

    @GetMapping("/cheatsheet")
    @Operation(summary = "내 치트시트 목록 조회")
    suspend fun listCheatsheets(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<List<ReportSummaryResponse>> {
        val list = cheatsheetService.getMyCheatsheets(principal.userId)
        return ServiceResponse.ok(list)
    }

    @GetMapping("/cheatsheet/{reportId}")
    @Operation(summary = "치트시트 상세 조회")
    suspend fun getCheatsheet(
        @PathVariable reportId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<ReportResponse> {
        val report = cheatsheetService.getCheatsheet(reportId, principal.userId)
        return ServiceResponse.ok(report)
    }

    @DeleteMapping("/cheatsheet/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "치트시트 삭제")
    suspend fun deleteCheatsheet(
        @PathVariable reportId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ) {
        cheatsheetService.deleteCheatsheet(reportId, principal.userId)
    }
}
