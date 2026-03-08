package com.insuranceai.chat.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/** 사용자 보험 질문 분석 결과 (Spring AI Structured Output용) */
@JsonClassDescription("사용자 보험 질문 분석 결과")
data class QuestionAnalysis(
    @JsonProperty(required = true)
    @JsonPropertyDescription("언급된 보험사명 목록 (예: [\"삼성생명\", \"한화생명\"])")
    val companies: List<String>,
    @JsonProperty(required = true)
    @JsonPropertyDescription("카테고리 목록 (PROTECTION, SAVINGS, ANNUITY 등)")
    val categories: List<String>,
    @JsonProperty(required = true)
    @JsonPropertyDescription("내용유형 목록 (COVERAGE, EXEMPTION, REDUCTION 등)")
    val contentTypes: List<String>,
    @JsonProperty(required = true)
    @JsonPropertyDescription("비교 질의 여부")
    val isComparison: Boolean,
    @JsonProperty(required = true)
    @JsonPropertyDescription("보충 데이터 필요 여부")
    val needsSupplementary: SupplementaryNeeds,
)

/** 보충 데이터 필요 여부 */
data class SupplementaryNeeds(
    @JsonPropertyDescription("해약환급금 필요 여부")
    val surrenderValue: Boolean = false,
    @JsonPropertyDescription("보험료 예시 필요 여부")
    val premiumExample: Boolean = false,
    @JsonPropertyDescription("가격지수 필요 여부")
    val priceIndex: Boolean = false,
    @JsonPropertyDescription("상품 특징 필요 여부")
    val productFeature: Boolean = false,
    @JsonPropertyDescription("수익률 시나리오 필요 여부")
    val yieldScenario: Boolean = false,
    @JsonPropertyDescription("상품 지표(손해율, 해약률 등) 필요 여부")
    val productMetric: Boolean = false,
)
