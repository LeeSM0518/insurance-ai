package com.insuranceai.evaluation.domain

import com.insuranceai.policy.domain.ContentType

/** 설계사 실무 카테고리 (Step 19 - 골든 데이터셋 생성용) */
enum class PracticeCategory(
    val description: String,
    val relatedContentTypes: List<ContentType>,
) {
    /** 보장 가능 여부 확인 */
    COVERAGE_CHECK(
        "보장 가능 여부 확인",
        listOf(ContentType.COVERAGE, ContentType.COVERAGE_DETAIL, ContentType.SPECIAL_TERMS),
    ),

    /** 면책/감액 조건 설명 */
    EXCLUSION_EXPLAIN(
        "면책/감액 조건 설명",
        listOf(ContentType.EXEMPTION, ContentType.REDUCTION),
    ),

    /** 보험금 청구 절차/서류 */
    CLAIM_PROCESS(
        "보험금 청구 절차/서류",
        listOf(ContentType.CLAIM_DOCUMENTS, ContentType.ARTICLE),
    ),

    /** 상품 간 비교 */
    PRODUCT_COMPARE(
        "상품 간 비교",
        listOf(ContentType.COVERAGE, ContentType.EXEMPTION),
    ),

    /** 특약 관련 질문 */
    SPECIAL_TERMS(
        "특약 관련 질문",
        listOf(ContentType.SPECIAL_TERMS, ContentType.COVERAGE_DETAIL),
    ),

    /** 조건부 보장 확인 */
    CONDITION_CHECK(
        "조건부 보장 확인",
        listOf(ContentType.EXEMPTION, ContentType.COVERAGE, ContentType.QUALIFICATION),
    ),

    /** 거절 대상 (약관 범위 외) */
    REJECTION(
        "거절 대상 (약관 범위 외)",
        emptyList(),
    ),
}
