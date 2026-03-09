package com.insuranceai.policy.controller

import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.policy.dto.PremiumExampleResponse
import com.insuranceai.policy.dto.ProductIndexResponse
import com.insuranceai.policy.dto.SurrenderValueResponse
import com.insuranceai.policy.service.PolicyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "상품", description = "보험 상품 보충 데이터 API")
@SecurityRequirement(name = "bearerAuth")
class ProductController(
    private val policyService: PolicyService,
) {
    @GetMapping("/{productId}/surrender-values")
    @Operation(summary = "해약환급금 조회")
    suspend fun getSurrenderValues(
        @PathVariable productId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<SurrenderValueResponse> {
        val response = policyService.getSurrenderValues(productId)
        return ServiceResponse.ok(response)
    }

    @GetMapping("/{productId}/premium-examples")
    @Operation(summary = "보험료 예시 조회")
    suspend fun getPremiumExamples(
        @PathVariable productId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<PremiumExampleResponse> {
        val response = policyService.getPremiumExamples(productId)
        return ServiceResponse.ok(response)
    }

    @GetMapping("/{productId}/indices")
    @Operation(summary = "가격지수 조회")
    suspend fun getProductIndices(
        @PathVariable productId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<ProductIndexResponse> {
        val response = policyService.getProductIndices(productId)
        return ServiceResponse.ok(response)
    }
}
