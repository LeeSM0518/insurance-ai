package com.insuranceai.policy.controller

import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.policy.domain.CategoryInfo
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.domain.ProductCategories
import com.insuranceai.policy.dto.InsuranceCompanyResponse
import com.insuranceai.policy.dto.PolicyArticleResponse
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.service.PolicyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "약관", description = "약관 조문 조회 API")
class PolicyController(
    private val policyService: PolicyService,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    @GetMapping("/policies/{articleId}")
    @Operation(summary = "약관 조문 상세 조회")
    @SecurityRequirement(name = "bearerAuth")
    suspend fun getArticleDetail(
        @PathVariable articleId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<PolicyArticleResponse> {
        val response = policyService.getArticleDetail(articleId)
        return ServiceResponse.ok(response)
    }

    @GetMapping("/policies/pdf/{dataSourceId}")
    @Operation(summary = "약관 PDF 파일 조회")
    @SecurityRequirement(name = "bearerAuth")
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

    @GetMapping("/insurance-companies")
    @Operation(summary = "보험사 목록 조회")
    suspend fun getInsuranceCompanies(): ServiceResponse<List<InsuranceCompanyResponse>> {
        val companies =
            insuranceCompanyRepository
                .findByIsActiveTrue()
                .map { entity ->
                    InsuranceCompanyResponse(
                        id = entity.id!!,
                        name = entity.name,
                        type = entity.typeEnum(),
                    )
                }.toList()
        return ServiceResponse.ok(companies)
    }

    @GetMapping("/categories")
    @Operation(summary = "카테고리 목록 조회")
    suspend fun getCategories(
        @RequestParam(required = false) insuranceType: InsuranceType?,
    ): ServiceResponse<List<CategoryInfo>> {
        val categories =
            if (insuranceType != null) {
                ProductCategories.findByInsuranceType(insuranceType)
            } else {
                ProductCategories.ALL
            }
        return ServiceResponse.ok(categories)
    }
}
