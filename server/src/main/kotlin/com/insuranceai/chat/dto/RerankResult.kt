package com.insuranceai.chat.dto

/** LLM Reranking 결과 — 관련성 높은 문서 인덱스를 순서대로 반환 */
data class RerankResult(
    val selectedIndices: List<Int>,
)
