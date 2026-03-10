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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "검색", description = "약관 검색 API")
class SearchController(
    private val searchService: SearchService,
) {
    @PostMapping
    @Operation(summary = "하이브리드 검색", description = "키워드+벡터+RRF 기반 약관 검색")
    @SecurityRequirement(name = "bearerAuth")
    suspend fun search(
        @Valid @RequestBody request: SearchRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<SearchResponse> {
        val response = searchService.search(request, principal.userId)
        return ServiceResponse.ok(response)
    }

    @GetMapping("/suggestions")
    @Operation(summary = "추천 검색어 목록")
    suspend fun getSuggestions(): ServiceResponse<List<String>> = ServiceResponse.ok(SUGGESTED_KEYWORDS)

    companion object {
        // Phase 1: 정적 추천 검색어 (추후 Phase 2에서 인기 검색어 DB 기반으로 전환)
        private val SUGGESTED_KEYWORDS =
            listOf(
                "암보험 면책기간",
                "실손 4세대",
                "치아보험 비교",
                "간병보험 보장",
                "운전자보험 벌금",
                "종신보험 해약환급금",
                "변액보험 수수료",
                "태아보험 가입시기",
            )
    }
}
