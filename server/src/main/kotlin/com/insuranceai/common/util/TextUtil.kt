package com.insuranceai.common.util

/**
 * 텍스트 정규화, 하이라이트, 변환 유틸리티
 */
object TextUtil {
    private val CAMEL_REGEX = "([a-z])([A-Z])".toRegex()

    /**
     * CamelCase → SCREAMING_SNAKE_CASE 변환
     * 예: SessionNotFound → SESSION_NOT_FOUND, LlmResponseFailed → LLM_RESPONSE_FAILED
     */
    fun String.toScreamingSnakeCase(): String = CAMEL_REGEX.replace(this, "$1_$2").uppercase()

    /**
     * 검색어 하이라이트 (pg_bigm 결과 후처리)
     * 키워드를 <mark> 태그로 감싸서 반환
     */
    fun highlightKeyword(
        text: String,
        keyword: String,
    ): String {
        if (keyword.isBlank()) return text
        return Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
            .replace(text) { "<mark>${it.value}</mark>" }
    }

    /**
     * 텍스트 정규화 (검색 전처리)
     * - 연속 공백 → 단일 공백
     * - 앞뒤 공백 제거
     * - 제어 문자 제거
     */
    fun normalize(text: String): String =
        text
            .replace(Regex("[\\p{Cntrl}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * 텍스트 snippet 생성 (검색 결과 미리보기)
     * 키워드 주변 문맥을 maxLength 이내로 추출
     */
    fun createSnippet(
        text: String,
        keyword: String,
        maxLength: Int = 200,
    ): String {
        if (text.length <= maxLength) return text
        if (keyword.isBlank()) return text.take(maxLength) + "..."

        val index = text.indexOf(keyword, ignoreCase = true)
        if (index == -1) return text.take(maxLength) + "..."

        // 키워드 중심으로 좌우 동일 길이 추출
        val contextSize = (maxLength - keyword.length) / 2
        val start = (index - contextSize).coerceAtLeast(0)
        val end = (index + keyword.length + contextSize).coerceAtMost(text.length)

        val snippet = text.substring(start, end)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return "$prefix$snippet$suffix"
    }
}
