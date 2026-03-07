package com.insuranceai.common.util

import com.insuranceai.common.util.TextUtil.toScreamingSnakeCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TextUtilTest {
    @Nested
    inner class ToScreamingSnakeCase {
        @Test
        fun `기본 CamelCase 변환`() {
            assertEquals("TOKEN_EXPIRED", "TokenExpired".toScreamingSnakeCase())
        }

        @Test
        fun `3단어 CamelCase 변환`() {
            assertEquals("SESSION_NOT_FOUND", "SessionNotFound".toScreamingSnakeCase())
        }

        @Test
        fun `연속 대문자 포함 변환`() {
            assertEquals("LLM_RESPONSE_FAILED", "LlmResponseFailed".toScreamingSnakeCase())
        }

        @Test
        fun `단일 단어 변환`() {
            assertEquals("UNAUTHORIZED", "Unauthorized".toScreamingSnakeCase())
        }

        @Test
        fun `이미 대문자인 경우`() {
            assertEquals("UNKNOWN", "UNKNOWN".toScreamingSnakeCase())
        }
    }

    @Nested
    inner class HighlightKeyword {
        @Test
        fun `키워드 하이라이트`() {
            val result = TextUtil.highlightKeyword("보험 약관을 검색합니다", "약관")
            assertEquals("보험 <mark>약관</mark>을 검색합니다", result)
        }

        @Test
        fun `대소문자 무시 하이라이트`() {
            val result = TextUtil.highlightKeyword("Hello World", "hello")
            assertEquals("<mark>Hello</mark> World", result)
        }

        @Test
        fun `빈 키워드는 원본 반환`() {
            val result = TextUtil.highlightKeyword("보험 약관", "")
            assertEquals("보험 약관", result)
        }
    }

    @Nested
    inner class Normalize {
        @Test
        fun `연속 공백 정리`() {
            assertEquals("보험 약관 검색", TextUtil.normalize("보험   약관  검색"))
        }

        @Test
        fun `앞뒤 공백 제거`() {
            assertEquals("보험 약관", TextUtil.normalize("  보험 약관  "))
        }

        @Test
        fun `제어 문자 제거`() {
            assertEquals("보험약관", TextUtil.normalize("보험\u0000약관"))
        }
    }

    @Nested
    inner class CreateSnippet {
        @Test
        fun `짧은 텍스트는 그대로 반환`() {
            val text = "짧은 텍스트"
            assertEquals(text, TextUtil.createSnippet(text, "짧은"))
        }

        @Test
        fun `키워드 주변 문맥 추출`() {
            val text = "a".repeat(100) + "키워드" + "b".repeat(100)
            val snippet = TextUtil.createSnippet(text, "키워드", maxLength = 50)
            assert(snippet.contains("키워드"))
            assert(snippet.startsWith("..."))
            assert(snippet.endsWith("..."))
        }

        @Test
        fun `키워드 없으면 앞부분 반환`() {
            val text = "a".repeat(300)
            val snippet = TextUtil.createSnippet(text, "없는키워드", maxLength = 200)
            assertEquals(200 + 3, snippet.length) // 200자 + "..."
        }

        @Test
        fun `빈 키워드는 앞부분 반환`() {
            val text = "a".repeat(300)
            val snippet = TextUtil.createSnippet(text, "", maxLength = 200)
            assertEquals(200 + 3, snippet.length)
        }
    }
}
