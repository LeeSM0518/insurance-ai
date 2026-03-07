package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComparisonDataParserTest {
    private val parser =
        ComparisonDataParser(
            crawlerCommon = io.mockk.mockk(),
        )

    @Nested
    inner class ExtractPremiumConditions {
        @Test
        fun `보험료 기준 줄에서 나이, 보험기간, 납입기간 추출`() {
            val notes = "보험료 비교기준: 40세 보험기간: 종신 납입기간: 20년납"
            val result = parser.extractPremiumConditions(notes)

            assertEquals(40, result.age)
            assertEquals("종신", result.insurancePeriod)
            assertEquals("20년납", result.paymentPeriod)
        }

        @Test
        fun `여러 줄 특이사항에서 보험료 기준 줄만 파싱`() {
            val notes =
                """
                상품 판매 중
                보험료 기준: 35세, 보험기간: 80세만기, 납입기간: 20년납
                기타 사항 있음
                """.trimIndent()
            val result = parser.extractPremiumConditions(notes)

            assertEquals(35, result.age)
            assertEquals("80세만기,", result.insurancePeriod)
            assertEquals("20년납", result.paymentPeriod)
        }

        @Test
        fun `비교기준 키워드로도 매칭`() {
            val notes = "비교기준 45세 보험기간:10년 납입기간:10년납"
            val result = parser.extractPremiumConditions(notes)

            assertEquals(45, result.age)
            assertEquals("10년", result.insurancePeriod)
            assertEquals("10년납", result.paymentPeriod)
        }

        @Test
        fun `notes가 null이면 ParsingException 발생`() {
            assertThrows<ParsingException> {
                parser.extractPremiumConditions(null)
            }
        }

        @Test
        fun `보험료 기준 줄이 없으면 ParsingException 발생`() {
            assertThrows<ParsingException> {
                parser.extractPremiumConditions("기타 사항만 있음")
            }
        }

        @Test
        fun `나이 추출 실패 시 ParsingException 발생`() {
            assertThrows<ParsingException> {
                parser.extractPremiumConditions("보험료 기준: 보험기간: 종신, 납입기간: 20년납")
            }
        }

        @Test
        fun `보험기간 추출 실패 시 ParsingException 발생`() {
            assertThrows<ParsingException> {
                parser.extractPremiumConditions("보험료 기준: 40세, 납입기간: 20년납")
            }
        }

        @Test
        fun `납입기간 추출 실패 시 ParsingException 발생`() {
            assertThrows<ParsingException> {
                parser.extractPremiumConditions("보험료 기준: 40세, 보험기간: 종신")
            }
        }
    }

    @Nested
    inner class ExtractPremiumConditionsNonLife {
        @Test
        fun `손보 패턴 A - 나이, 만기, 납입기간 추출`() {
            val notes = "40세, 상해1급, 100세만기, 20년납 기준"
            val result = parser.extractPremiumConditions(notes)

            assertEquals(40, result.age)
            assertEquals("100세만기", result.insurancePeriod)
            assertEquals("20년납", result.paymentPeriod)
        }

        @Test
        fun `손보 패턴 B - 재물보험 나이 없음 (PB11)`() {
            val notes = "아파트, 건물1급, 100㎡, 10년만기 전기납 기준"
            val result = parser.extractPremiumConditions(notes, subcategory = "PB11")

            assertNull(result.age)
            assertEquals("10년만기", result.insurancePeriod)
            assertEquals("전기납", result.paymentPeriod)
        }

        @Test
        fun `손보 패턴 B - 재물보험 나이 없음 (PB12)`() {
            val notes = "단독주택, 건물2급, 50㎡, 5년만기 전기납 기준"
            val result = parser.extractPremiumConditions(notes, subcategory = "PB12")

            assertNull(result.age)
            assertEquals("5년만기", result.insurancePeriod)
            assertEquals("전기납", result.paymentPeriod)
        }

        @Test
        fun `손보 패턴 C - 실손보험 1년만기`() {
            val notes = "60세, 상해1급, 1년만기 1년납"
            val result = parser.extractPremiumConditions(notes)

            assertEquals(60, result.age)
            assertEquals("1년만기", result.insurancePeriod)
            assertEquals("1년납", result.paymentPeriod)
        }

        @Test
        fun `손보 패턴 D - 어린이보험`() {
            val notes = "5세, 1급, 100세만기, 20년납"
            val result = parser.extractPremiumConditions(notes)

            assertEquals(5, result.age)
            assertEquals("100세만기", result.insurancePeriod)
            assertEquals("20년납", result.paymentPeriod)
        }

        @Test
        fun `손보 패턴 E - 치아 보험기간 납입기간 형식 (나이 포함)`() {
            val notes = "40세, 보험기간 10년, 납입기간 10년"
            val result = parser.extractPremiumConditions(notes, subcategory = "PB17")

            assertEquals(40, result.age)
            assertEquals("10년", result.insurancePeriod)
            assertEquals("10년", result.paymentPeriod)
        }

        @Test
        fun `손보 패턴 E - 보험기간 납입기간 형식 (나이 없이 생보 패턴 매칭)`() {
            val notes = "보험료 기준: 보험기간: 10년 납입기간: 10년"
            val result = parser.extractPremiumConditions(notes, subcategory = "PB12")

            assertNull(result.age)
            assertEquals("10년", result.insurancePeriod)
            assertEquals("10년", result.paymentPeriod)
        }

        @Test
        fun `인보험 subcategory인데 나이 없으면 ParsingException`() {
            // "100세만기"의 "세"는 보험기간이므로 나이로 추출되면 안 됨
            val notes = "상해1급, 100세만기, 20년납 기준"
            assertThrows<ParsingException> {
                parser.extractPremiumConditions(notes, subcategory = "PB13")
            }
        }

        @Test
        fun `인보험 subcategory 생보패턴에서도 나이 없으면 ParsingException`() {
            val notes = "보험료 기준: 보험기간: 종신 납입기간: 20년납"
            assertThrows<ParsingException> {
                parser.extractPremiumConditions(notes, subcategory = "PB13")
            }
        }

        @Test
        fun `생보 기존 패턴 회귀 테스트`() {
            val notes = "보험료 비교기준: 40세 보험기간: 종신 납입기간: 20년납"
            val result = parser.extractPremiumConditions(notes, subcategory = "WHOLE_LIFE")

            assertEquals(40, result.age)
            assertEquals("종신", result.insurancePeriod)
            assertEquals("20년납", result.paymentPeriod)
        }
    }

    @Nested
    inner class FindValueFallback {
        @Test
        fun `갱신여부 키워드 우선 매칭 - 예상갱신보험료 미매칭`() {
            val headers = listOf("회사명", "상품명", "예상갱신보험료", "갱신여부")
            // "갱신여부"가 있으면 "예상갱신보험료"보다 우선 매칭되어야 함
            val idx = HtmlExcelParser.findColumnIndex(headers, "갱신여부")
            assertEquals(3, idx)
        }

        @Test
        fun `채널 키워드 fallback 매칭`() {
            val headers = listOf("회사명", "상품명", "채널")
            val idx = HtmlExcelParser.findColumnIndex(headers, "채널")
            assertEquals(2, idx)
        }
    }
}
