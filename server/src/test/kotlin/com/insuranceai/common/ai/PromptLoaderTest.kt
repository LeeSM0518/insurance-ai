package com.insuranceai.common.ai

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptLoaderTest {
    private val promptLoader = PromptLoader()

    @Nested
    inner class Load {
        @Test
        fun `존재하는 파일 로드 성공`() {
            val content = promptLoader.load("chat/system-prompt.st")
            assertTrue(content.contains("보험 설계사를 위한 AI 영업 비서"))
        }

        @Test
        fun `존재하지 않는 파일 로드 시 예외`() {
            assertThrows<Exception> {
                promptLoader.load("nonexistent/file.st")
            }
        }
    }

    @Nested
    inner class Render {
        @Test
        fun `변수 치환 동작 확인`() {
            val result = promptLoader.render("test/template.st", mapOf("name" to "보험AI"))
            assertTrue(result.contains("보험AI"))
        }
    }

    @Nested
    inner class Cache {
        @Test
        fun `같은 경로 2회 호출 시 같은 결과 반환`() {
            val content1 = promptLoader.load("chat/system-prompt.st")
            val content2 = promptLoader.load("chat/system-prompt.st")
            assertEquals(content1, content2)
        }
    }
}
