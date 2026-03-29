package com.insuranceai.report.controller

import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.report.dto.SharedReportResponse
import com.insuranceai.report.error.ReportError
import com.insuranceai.report.service.ReportShareService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/shared")
@Tag(name = "SharedReport", description = "공유 리포트 공개 API (인증 불필요)")
class SharedReportController(
    private val reportShareService: ReportShareService,
) {
    @GetMapping("/{shareToken}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "공유 리포트 조회 (공개)")
    suspend fun getSharedReport(
        @PathVariable shareToken: String,
    ): ServiceResponse<SharedReportResponse> {
        val report = reportShareService.getSharedReport(shareToken)
        return ServiceResponse.ok(report)
    }

    /** 브라우저/크롤러 접근 시 OG 메타태그 포함 HTML 반환 → SPA로 리다이렉트 */
    @GetMapping("/{shareToken}", produces = [MediaType.TEXT_HTML_VALUE])
    @Operation(summary = "공유 리포트 HTML (OG 메타태그)")
    suspend fun getSharedReportHtml(
        @PathVariable shareToken: String,
    ): ResponseEntity<String> {
        val report =
            try {
                reportShareService.getSharedReport(shareToken)
            } catch (e: ReportError) {
                return ResponseEntity.notFound().build()
            }
        val escapedTitle =
            (report.title ?: "보장 분석 리포트")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
        val html =
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="$escapedTitle" />
                <meta property="og:description" content="AI 기반 보험 비교 분석 리포트" />
                <meta property="og:type" content="article" />
                <meta charset="utf-8" />
                <script>window.location.href = '/shared/$shareToken';</script>
            </head>
            <body>리포트를 불러오는 중...</body>
            </html>
            """.trimIndent()
        return ResponseEntity.ok(html)
    }
}
