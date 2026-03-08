package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueCleanerTest {
    @Nested
    inner class IsNull {
        @Test
        fun `null 표현 인식`() {
            assertTrue(ValueCleaner.isNull(null))
            assertTrue(ValueCleaner.isNull("-"))
            assertTrue(ValueCleaner.isNull("해당없음"))
            assertTrue(ValueCleaner.isNull("NaN"))
            assertTrue(ValueCleaner.isNull(""))
            assertTrue(ValueCleaner.isNull(" "))
        }

        @Test
        fun `일반 값은 null이 아님`() {
            assertFalse(ValueCleaner.isNull("삼성생명"))
            assertFalse(ValueCleaner.isNull("836000"))
        }
    }

    @Nested
    inner class CleanAmount {
        @Test
        fun `금액 클렌징 - 콤마 공백 원 제거`() {
            assertEquals(836000L, ValueCleaner.cleanAmount("836000  원"))
            assertEquals(806000L, ValueCleaner.cleanAmount("806,000  원"))
            assertEquals(1000000L, ValueCleaner.cleanAmount("1,000,000"))
        }

        @Test
        fun `금액 클렌징 - 만원 단위`() {
            assertEquals(100_000_000L, ValueCleaner.cleanAmount("10,000만원"))
            assertEquals(50_000_000L, ValueCleaner.cleanAmount("5000만원"))
        }

        @Test
        fun `금액 클렌징 - 억원 단위`() {
            assertEquals(100_000_000L, ValueCleaner.cleanAmount("1억원"))
            assertEquals(250_000_000L, ValueCleaner.cleanAmount("2.5억원"))
        }

        @Test
        fun `null 표현은 null 반환`() {
            assertNull(ValueCleaner.cleanAmount("-"))
            assertNull(ValueCleaner.cleanAmount("해당없음"))
            assertNull(ValueCleaner.cleanAmount(null))
        }
    }

    @Nested
    inner class CleanDecimal {
        @Test
        fun `이율 클렌징`() {
            assertEquals(0, BigDecimal("2.70").compareTo(ValueCleaner.cleanDecimal("2.70 %")))
            assertEquals(0, BigDecimal("132.84").compareTo(ValueCleaner.cleanDecimal("132.84")))
        }

        @Test
        fun `null 표현은 null 반환`() {
            assertNull(ValueCleaner.cleanDecimal("-"))
            assertNull(ValueCleaner.cleanDecimal(null))
        }

        @Test
        fun `괄호 형식 이율 - 괄호 앞 숫자만 추출`() {
            assertEquals(0, BigDecimal("1.90").compareTo(ValueCleaner.cleanDecimal("1.90(0.3)")))
        }

        @Test
        fun `복잡한 괄호 형식 - 괄호 앞 숫자만 추출`() {
            assertEquals(
                0,
                BigDecimal("1.95").compareTo(
                    ValueCleaner.cleanDecimal("1.95(5년이하 1.25, 5년초과 15년이하 : 1.0, 15년초과 : 0.3)"),
                ),
            )
        }
    }

    @Nested
    inner class ParseGuaranteedRate {
        @Test
        fun `순수 단일 이율 — 해당 값 반환`() {
            assertEquals(0, BigDecimal("0.5").compareTo(ValueCleaner.parseGuaranteedRate("0.5%")))
        }

        @Test
        fun `순수 단일 이율(퍼센트 없음) — 해당 값 반환`() {
            assertEquals(0, BigDecimal("2.70").compareTo(ValueCleaner.parseGuaranteedRate("2.70")))
        }

        @Test
        fun `단순 괄호(2개 이상 이율) — null 반환`() {
            assertNull(ValueCleaner.parseGuaranteedRate("1.90(0.3)"))
        }

        @Test
        fun `기간별 괄호(3개 이상 이율) — null 반환`() {
            assertNull(
                ValueCleaner.parseGuaranteedRate("1.95(5년이하 1.25, 5년초과 15년이하 : 1.0, 15년초과 : 0.3)"),
            )
        }

        @Test
        fun `복합 이율 텍스트(3개 이상) — null 반환`() {
            assertNull(
                ValueCleaner.parseGuaranteedRate(
                    "3년 이내 연복리 1.0%, 3년 초과 5년 이내 연복리 0.75%, 5년 초과 연복리 0.5%",
                ),
            )
        }

        @Test
        fun `null 표현은 null 반환`() {
            assertNull(ValueCleaner.parseGuaranteedRate("-"))
            assertNull(ValueCleaner.parseGuaranteedRate(null))
        }
    }

    @Nested
    inner class IsCompoundRate {
        @Test
        fun `단일 이율 — false`() {
            assertFalse(ValueCleaner.isCompoundRate("0.5%"))
            assertFalse(ValueCleaner.isCompoundRate("2.70"))
        }

        @Test
        fun `단순 괄호(2개 이율) — true`() {
            assertTrue(ValueCleaner.isCompoundRate("1.90(0.3)"))
        }

        @Test
        fun `기간별 괄호(3개 이상 이율) — true`() {
            assertTrue(ValueCleaner.isCompoundRate("1.95(5년이하 1.25, 5년초과 15년이하 : 1.0, 15년초과 : 0.3)"))
        }

        @Test
        fun `복합 텍스트(3개 이상) — true`() {
            assertTrue(
                ValueCleaner.isCompoundRate(
                    "3년 이내 연복리 1.0%, 3년 초과 5년 이내 연복리 0.75%, 5년 초과 연복리 0.5%",
                ),
            )
        }

        @Test
        fun `null 표현 — false`() {
            assertFalse(ValueCleaner.isCompoundRate(null))
            assertFalse(ValueCleaner.isCompoundRate("-"))
        }
    }

    @Nested
    inner class FormatGuaranteedRateText {
        @Test
        fun `단순 괄호 — 공시이율, 최저보증이율 형식`() {
            assertEquals("공시이율 1.90%, 최저보증이율 0.3%", ValueCleaner.formatGuaranteedRateText("1.90(0.3)"))
        }

        @Test
        fun `기간별 복합 텍스트 — 원본 그대로`() {
            val original = "3년 이내 연복리 1.0%, 3년 초과 5년 이내 연복리 0.75%, 5년 초과 연복리 0.5%"
            assertEquals(original, ValueCleaner.formatGuaranteedRateText(original))
        }

        @Test
        fun `기간별 괄호 — 원본 그대로`() {
            val original = "1.95(5년이하 1.25, 5년초과 15년이하 : 1.0, 15년초과 : 0.3)"
            assertEquals(original, ValueCleaner.formatGuaranteedRateText(original))
        }

        @Test
        fun `null 표현 — null 반환`() {
            assertNull(ValueCleaner.formatGuaranteedRateText(null))
            assertNull(ValueCleaner.formatGuaranteedRateText("-"))
        }
    }

    @Nested
    inner class CleanRenewalType {
        @Test
        fun `갱신유형 정규화`() {
            assertEquals("RENEWAL", ValueCleaner.cleanRenewalType("갱신형"))
            assertEquals("NON_RENEWAL", ValueCleaner.cleanRenewalType("비갱신형"))
            assertNull(ValueCleaner.cleanRenewalType("-"))
        }
    }

    @Nested
    inner class IsUniversal {
        @Test
        fun `유니버셜 판별`() {
            assertTrue(ValueCleaner.isUniversal("무배당 삼성 유니버셜 종신보험"))
            assertTrue(ValueCleaner.isUniversal("UL종신보험"))
            assertFalse(ValueCleaner.isUniversal("무배당 삼성 종신보험"))
        }
    }

    @Nested
    inner class CleanDate {
        @Test
        fun `날짜 파싱`() {
            assertEquals(LocalDate.of(2024, 3, 15), ValueCleaner.cleanDate("2024.03.15"))
            assertEquals(LocalDate.of(2024, 3, 15), ValueCleaner.cleanDate("2024-03-15"))
            assertNull(ValueCleaner.cleanDate("-"))
        }
    }

    @Nested
    inner class ParseElapsedMonths {
        @Test
        fun `유지기간 변환`() {
            assertEquals(12, ValueCleaner.parseElapsedMonths("1년"))
            assertEquals(36, ValueCleaner.parseElapsedMonths("3년"))
            assertEquals(6, ValueCleaner.parseElapsedMonths("6개월"))
            assertEquals(3, ValueCleaner.parseElapsedMonths("0.25"))
        }

        @Test
        fun `null 표현은 null 반환`() {
            assertNull(ValueCleaner.parseElapsedMonths("-"))
        }
    }

    @Nested
    inner class CleanSurrenderType {
        @Test
        fun `해약환급금 유형 정규화`() {
            assertEquals("무해지저해지", ValueCleaner.cleanSurrenderType("무해지환급형"))
            assertEquals("순수보장", ValueCleaner.cleanSurrenderType("순수보장형"))
            assertEquals("만기환급", ValueCleaner.cleanSurrenderType("만기환급형"))
            assertEquals("일반", ValueCleaner.cleanSurrenderType("일반형"))
        }
    }

    @Nested
    inner class CleanPhone {
        @Test
        fun `전화번호 정규화`() {
            assertEquals("1588-1001", ValueCleaner.cleanPhone("1588-1001"))
            assertEquals("02-1588-1001", ValueCleaner.cleanPhone("02-1588-1001"))
            assertNull(ValueCleaner.cleanPhone("-"))
        }
    }

    @Nested
    inner class CleanPeriod {
        @Test
        fun `유효 기간 값 — 그대로 반환`() {
            assertEquals("종신", ValueCleaner.cleanPeriod("종신"))
            assertEquals("20년납", ValueCleaner.cleanPeriod("20년납"))
            assertEquals("100세만기", ValueCleaner.cleanPeriod("100세만기"))
            assertEquals("전기납", ValueCleaner.cleanPeriod("전기납"))
            assertEquals("일시납", ValueCleaner.cleanPeriod("일시납"))
            assertEquals("10년만기", ValueCleaner.cleanPeriod("10년만기"))
            assertEquals("10년", ValueCleaner.cleanPeriod("10년"))
            assertEquals("6개월", ValueCleaner.cleanPeriod("6개월"))
        }

        @Test
        fun `비정상 값 — null 반환`() {
            assertNull(ValueCleaner.cleanPeriod("별"))
            assertNull(ValueCleaner.cleanPeriod("별로"))
            assertNull(ValueCleaner.cleanPeriod("및"))
            assertNull(ValueCleaner.cleanPeriod(","))
            assertNull(ValueCleaner.cleanPeriod("중"))
            assertNull(ValueCleaner.cleanPeriod("[순수보장형]"))
            assertNull(ValueCleaner.cleanPeriod("에"))
        }

        @Test
        fun `null 입력 — null 반환`() {
            assertNull(ValueCleaner.cleanPeriod(null))
            assertNull(ValueCleaner.cleanPeriod("-"))
        }
    }

    @Nested
    inner class CleanGender {
        @Test
        fun `성별 정규화`() {
            assertEquals("남", ValueCleaner.cleanGender("남자"))
            assertEquals("남", ValueCleaner.cleanGender("M"))
            assertEquals("여", ValueCleaner.cleanGender("여자"))
            assertEquals("여", ValueCleaner.cleanGender("F"))
        }
    }

    // --- ParseResult 반환 메서드 테스트 ---

    @Nested
    inner class ParseAmount {
        @Test
        fun `정상 값 — Success 반환`() {
            val result = ValueCleaner.parseAmount("836000  원")
            check(result is ParseResult.Success)
            assertEquals(836000L, result.value)
        }

        @Test
        fun `null 표현 — Empty 반환`() {
            assertTrue(ValueCleaner.parseAmount(null) is ParseResult.Empty)
            assertTrue(ValueCleaner.parseAmount("-") is ParseResult.Empty)
            assertTrue(ValueCleaner.parseAmount("해당없음") is ParseResult.Empty)
        }

        @Test
        fun `미지원 포맷 — Failure 반환`() {
            val result = ValueCleaner.parseAmount("약 천만원")
            check(result is ParseResult.Failure)
            assertEquals("약 천만원", result.rawValue)
        }

        @Test
        fun `텍스트 금액(실손의료보험) — Empty 반환`() {
            assertTrue(ValueCleaner.parseAmount("용어해설참조") is ParseResult.Empty)
            assertTrue(ValueCleaner.parseAmount("용어해설 참조") is ParseResult.Empty)
        }
    }

    @Nested
    inner class ParseDecimalResult {
        @Test
        fun `정상 값 — Success 반환`() {
            val result = ValueCleaner.parseDecimal("2.70 %")
            check(result is ParseResult.Success)
            assertEquals(0, BigDecimal("2.70").compareTo(result.value))
        }

        @Test
        fun `null 표현 — Empty 반환`() {
            assertTrue(ValueCleaner.parseDecimal(null) is ParseResult.Empty)
            assertTrue(ValueCleaner.parseDecimal("-") is ParseResult.Empty)
        }

        @Test
        fun `미지원 포맷 — Failure 반환`() {
            val result = ValueCleaner.parseDecimal("미공시")
            assertTrue(result is ParseResult.Failure)
        }

        @Test
        fun `복합 이율 텍스트 — Empty 반환`() {
            val result =
                ValueCleaner.parseDecimal(
                    "3년 이내 연복리 1.0%, 3년 초과 5년 이내 연복리 0.75%, 5년 초과 연복리 0.5%",
                )
            assertTrue(result is ParseResult.Empty)
        }
    }

    @Nested
    inner class ParseDateResult {
        @Test
        fun `정상 값 — Success 반환`() {
            val result = ValueCleaner.parseDate("2024.03.15")
            check(result is ParseResult.Success)
            assertEquals(LocalDate.of(2024, 3, 15), result.value)
        }

        @Test
        fun `null 표현 — Empty 반환`() {
            assertTrue(ValueCleaner.parseDate(null) is ParseResult.Empty)
            assertTrue(ValueCleaner.parseDate("-") is ParseResult.Empty)
        }

        @Test
        fun `미지원 포맷 — Failure 반환`() {
            val result = ValueCleaner.parseDate("2024년 3월")
            check(result is ParseResult.Failure)
            assertEquals("2024년 3월", result.rawValue)
        }
    }

    @Nested
    inner class ParseIntResult {
        @Test
        fun `정상 값 — Success 반환`() {
            val result = ValueCleaner.parseInt("42")
            check(result is ParseResult.Success)
            assertEquals(42, result.value)
        }

        @Test
        fun `null 표현 — Empty 반환`() {
            assertTrue(ValueCleaner.parseInt(null) is ParseResult.Empty)
            assertTrue(ValueCleaner.parseInt("-") is ParseResult.Empty)
        }
    }
}
