package com.insuranceai.policy.domain

/** 약관 구분: policy_articles.section_type */
enum class SectionType {
    MAIN,
    RIDER,
    ;

    companion object {
        /** LLM 등 외부 입력의 sectionType 문자열을 정규화 (알 수 없는 값은 MAIN으로 fallback) */
        fun normalize(value: String?): String {
            if (value == null) return MAIN.name
            return when (value.uppercase().trim()) {
                MAIN.name, "MAIN_CONTRACT", "주계약" -> MAIN.name
                RIDER.name, "특약" -> RIDER.name
                else -> MAIN.name
            }
        }
    }
}
