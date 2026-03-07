package com.insuranceai.chat.dto

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/** 추천 질문 목록 (Spring AI Structured Output용) */
@JsonClassDescription("추천 질문 목록")
data class SuggestedQuestions(
    @JsonProperty(required = true)
    @JsonPropertyDescription("후속 질문 3개")
    val questions: List<String>,
)
