package com.insuranceai.search.controller

import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.search.dto.SearchRequest
import com.insuranceai.search.dto.SearchResponse
import com.insuranceai.search.service.SearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "검색", description = "약관 검색 API")
@SecurityRequirement(name = "bearerAuth")
class SearchController(
    private val searchService: SearchService,
) {
    @PostMapping
    @Operation(summary = "하이브리드 검색", description = "키워드+벡터+RRF 기반 약관 검색")
    suspend fun search(
        @Valid @RequestBody request: SearchRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<SearchResponse> {
        val response = searchService.search(request, principal.userId)
        return ServiceResponse.ok(response)
    }
}
