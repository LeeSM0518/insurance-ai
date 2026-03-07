package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseResultTest {
    @Nested
    inner class GetOrNull {
        @Test
        fun `Success에서 값 반환`() {
            val result: ParseResult<Long> = ParseResult.Success(1000L)
            assertEquals(1000L, result.getOrNull())
        }

        @Test
        fun `Empty에서 null 반환`() {
            val result: ParseResult<Long> = ParseResult.Empty
            assertNull(result.getOrNull())
        }

        @Test
        fun `Failure에서 null 반환`() {
            val result: ParseResult<Long> = ParseResult.Failure("abc", "파싱 실패")
            assertNull(result.getOrNull())
        }
    }

    @Nested
    inner class IsFailure {
        @Test
        fun `Success는 false`() {
            assertFalse(ParseResult.Success(42).isFailure())
        }

        @Test
        fun `Empty는 false`() {
            assertFalse(ParseResult.Empty.isFailure())
        }

        @Test
        fun `Failure는 true`() {
            assertTrue(ParseResult.Failure("abc", "실패").isFailure())
        }
    }
}
