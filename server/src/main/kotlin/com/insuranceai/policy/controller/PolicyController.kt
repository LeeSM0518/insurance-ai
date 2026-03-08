package com.insuranceai.policy.controller

import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.policy.dto.PolicyArticleResponse
import com.insuranceai.policy.service.PolicyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "약관", description = "약관 조문 조회 API")
@SecurityRequirement(name = "bearerAuth")
class PolicyController(
    private val policyService: PolicyService,
) {
    @GetMapping("/{articleId}")
    @Operation(summary = "약관 조문 상세 조회")
    suspend fun getArticleDetail(
        @PathVariable articleId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ServiceResponse<PolicyArticleResponse>> {
        val response = policyService.getArticleDetail(articleId)
        return ResponseEntity.ok(ServiceResponse.ok(response))
    }

    @GetMapping("/pdf/{dataSourceId}")
    @Operation(summary = "약관 PDF 파일 조회")
    suspend fun getPdf(
        @PathVariable dataSourceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Resource> {
        val resource = policyService.getPdfResource(dataSourceId)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header("X-Frame-Options", "SAMEORIGIN")
            .body(resource)
    }
}
