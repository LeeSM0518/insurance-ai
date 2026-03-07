package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

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
}
