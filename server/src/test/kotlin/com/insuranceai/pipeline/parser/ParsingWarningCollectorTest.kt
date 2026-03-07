package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParsingWarningCollectorTest {
    @Nested
    inner class CollectOrNull {
        @Test
        fun `Success — 값 반환, 경고 수집 안 함`() {
            val collector = ParsingWarningCollector()
            val result = collector.collectOrNull(ParseResult.Success(100L), "amount", "삼성|종신")
            assertEquals(100L, result)
            assertEquals(0, collector.failureCount)
        }

        @Test
        fun `Empty — null 반환, 경고 수집 안 함`() {
            val collector = ParsingWarningCollector()
            val result = collector.collectOrNull(ParseResult.Empty, "amount", "삼성|종신")
            assertNull(result)
            assertEquals(0, collector.failureCount)
        }

        @Test
        fun `Failure — null 반환, 경고 수집`() {
            val collector = ParsingWarningCollector()
            val result =
                collector.collectOrNull(
                    ParseResult.Failure("abc원", "금액 파싱 실패"),
                    "amount",
                    "삼성|종신",
                )
            assertNull(result)
            assertEquals(1, collector.failureCount)
            assertEquals("amount", collector.warnings.first().field)
            assertEquals("abc원", collector.warnings.first().rawValue)
            assertEquals("삼성|종신", collector.warnings.first().context)
        }
    }

    @Nested
    inner class Summary {
        @Test
        fun `경고 없으면 빈 메시지`() {
            val collector = ParsingWarningCollector()
            assertEquals("파싱 경고 없음", collector.summary())
        }

        @Test
        fun `경고 있으면 필드별 그룹핑 요약`() {
            val collector = ParsingWarningCollector()
            collector.add("salesStartDate", "2024년 3월", "날짜 포맷 매칭 실패", "삼성|종신")
            collector.add("salesStartDate", "미정", "날짜 포맷 매칭 실패", "한화|정기")
            collector.add("appliedRate", "미공시", "소수 파싱 실패", "삼성|종신")

            val summary = collector.summary()
            assertTrue(summary.contains("파싱 경고 3건"))
            assertTrue(summary.contains("salesStartDate: 2건"))
            assertTrue(summary.contains("appliedRate: 1건"))
        }
    }
}
