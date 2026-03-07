package com.insuranceai.pipeline.parser

import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler.NonLifeCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NonLifeJsonParserTest {
    private val testChecksum: (ByteArray) -> String = { "test-checksum" }

    @Nested
    inner class ParseJson5Test {
        @Test
        fun `장기보장성 JSON 파싱`() {
            val json = "{list:[{TP_CODE:'T001',P_CODE_NM:'삼성화재',TP_NAME:'화재보험',TP_M_BILL:45774}]}"
            val result = NonLifeJsonParser.parseJson5(json)

            assertEquals(1, result.size)
            assertEquals("T001", result[0]["TP_CODE"])
            assertEquals("삼성화재", result[0]["P_CODE_NM"])
            assertEquals("화재보험", result[0]["TP_NAME"])
        }

        @Test
        fun `빈 응답`() {
            assertEquals(emptyList<Any>(), NonLifeJsonParser.parseJson5("{list:[]}"))
            assertEquals(emptyList<Any>(), NonLifeJsonParser.parseJson5(""))
            assertEquals(emptyList<Any>(), NonLifeJsonParser.parseJson5("   "))
        }

        @Test
        fun `값 내 줄바꿈 1개 처리`() {
            val json = "{list:[{TP_CODE:'T001',P_CODE_NM:'회사',TP_ETC:'첫째줄\n둘째줄'}]}"
            val result = NonLifeJsonParser.parseJson5(json)

            assertEquals(1, result.size)
            val etc = result[0]["TP_ETC"] as String
            assertTrue(etc.contains("\\n") || etc.contains("첫째줄"))
        }

        @Test
        fun `값 내 줄바꿈 여러 개 처리`() {
            val json = "{list:[{TP_CODE:'T001',P_CODE_NM:'회사',TP_ETC:'줄1\n줄2\n줄3'}]}"
            val result = NonLifeJsonParser.parseJson5(json)

            assertEquals(1, result.size)
        }

        @Test
        fun `숫자 값 타입 유지`() {
            val json = "{list:[{TP_CODE:'T001',TP_M_BILL:45774,TP_RATE1:2.70}]}"
            val result = NonLifeJsonParser.parseJson5(json)

            assertEquals(1, result.size)
            // Jackson이 숫자 타입을 자동 판별
            assertTrue(result[0]["TP_M_BILL"] is Number)
            assertTrue(result[0]["TP_RATE1"] is Number)
        }

        @Test
        fun `여러 행 파싱`() {
            val json = "{list:[{TP_CODE:'T001',P_CODE_NM:'회사A'},{TP_CODE:'T002',P_CODE_NM:'회사B'}]}"
            val result = NonLifeJsonParser.parseJson5(json)

            assertEquals(2, result.size)
            assertEquals("회사A", result[0]["P_CODE_NM"])
            assertEquals("회사B", result[1]["P_CODE_NM"])
        }
    }

    @Nested
    inner class ToProductRawDataProtectionTest {
        private val category = NonLifeCategory(1, "장기보장성", "PB14", "상해")

        @Test
        fun `장기보장성 상품 그룹핑`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험A",
                        "TP_PAY_NAME" to "사망",
                        "TP_PAY_REASON" to "사망 시",
                        "TP_PAY" to "1억원",
                        "TP_M_BILL" to 45774,
                    ),
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험A",
                        "TP_PAY_NAME" to "입원",
                        "TP_PAY_REASON" to "입원 시",
                        "TP_PAY" to "5만원",
                        "TP_M_BILL" to 45774,
                    ),
                    mapOf<String, Any>(
                        "TP_CODE" to "T002",
                        "P_CODE_NM" to "현대해상",
                        "TP_NAME" to "화재보험B",
                        "TP_PAY_NAME" to "사망",
                        "TP_PAY_REASON" to "사망 시",
                        "TP_PAY" to "1억원",
                        "TP_M_BILL" to 30000,
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)

            // T001, T002 2개 상품으로 그룹핑
            assertEquals(2, result.size)
        }

        @Test
        fun `담보 여러 개인 상품`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험",
                        "TP_PAY_NAME" to "사망",
                        "TP_PAY_REASON" to "사망 시",
                        "TP_PAY" to "1억원",
                        "TP_M_BILL" to 45774,
                    ),
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험",
                        "TP_PAY_NAME" to "입원",
                        "TP_PAY_REASON" to "입원 시",
                        "TP_PAY" to "5만원",
                        "TP_M_BILL" to 45774,
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)

            assertEquals(1, result.size)
            assertEquals(2, result[0].coverages.size)
            assertEquals("사망", result[0].coverages[0].coverageName)
            assertEquals("입원", result[0].coverages[1].coverageName)
        }

        @Test
        fun `보험료 남녀 추출`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험",
                        "TP_PAY_NAME" to "사망",
                        "TP_PAY_REASON" to "",
                        "TP_PAY" to "",
                        "TP_M_BILL" to 45774,
                        "TP_W_BILL" to 38000,
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)

            assertEquals(1, result.size)
            assertEquals(2, result[0].premiumExamples.size)
            assertEquals("남", result[0].premiumExamples[0].gender)
            assertEquals(45774L, result[0].premiumExamples[0].premium)
            assertEquals("여", result[0].premiumExamples[1].gender)
            assertEquals(38000L, result[0].premiumExamples[1].premium)
        }

        @Test
        fun `지수 추출`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험",
                        "TP_PAY_NAME" to "사망",
                        "TP_PAY_REASON" to "",
                        "TP_PAY" to "",
                        "INS_PAY_INDEX_M" to 95.5,
                        "INS_PAY_INDEX_W" to 97.2,
                        "CNTRCT_CNCLS_IDX" to 88.0,
                        "ADD_INSRNC_IDX" to 12.3,
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)

            assertEquals(1, result.size)
            assertEquals(4, result[0].indices.size)

            val priceIndices = result[0].indices.filter { it.indexType == "PRICE" }
            assertEquals(2, priceIndices.size)

            val contractCost = result[0].indices.first { it.indexType == "CONTRACT_COST" }
            assertNotNull(contractCost)
        }

        @Test
        fun `갱신유형 변환`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "T001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험",
                        "TP_PAY_NAME" to "사망",
                        "TP_PAY_REASON" to "",
                        "TP_PAY" to "",
                        "REFRESH_YN" to "Y",
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)
            assertEquals("RENEWAL", result[0].renewalType)
        }
    }

    @Nested
    inner class ToProductRawDataSavingsTest {
        private val category = NonLifeCategory(2, "장기저축성", "PC12", "장기상해보험_및_기타")

        @Test
        fun `장기저축성 변환`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "S001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "저축보험A",
                        "TP_RATE1" to "2.50",
                        "TP_RATE2" to "1.50",
                        "ETC5" to "1588-1234",
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)

            assertEquals(1, result.size)
            assertEquals("NL_SAVINGS", result[0].category)
            assertEquals("PC12", result[0].subcategory)
            assertNotNull(result[0].declaredInterestRate)
            assertNotNull(result[0].guaranteedInterestRate)
            assertEquals("1588-1234", result[0].phone)
        }
    }

    @Nested
    inner class ToProductRawDataPensionTest {
        private val category = NonLifeCategory(4, "연금저축", "PC99", "연금저축")

        @Test
        fun `연금저축 스키마`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "P001",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "연금저축보험A",
                        "TP_M_REFUND" to 5000000,
                        "BUSINESS_RATE" to "8.5",
                        "TP_RATE1" to "2.70",
                        "TP_RATE2" to "1.25",
                    ),
                )

            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)

            assertEquals(1, result.size)
            assertEquals("NL_PENSION", result[0].category)
            assertEquals("PC99", result[0].subcategory)
            assertTrue(result[0].surrenderValues.isNotEmpty())
            assertEquals(5000000L, result[0].surrenderValues[0].surrenderValue)
            assertNotNull(result[0].expenseRatio)
        }
    }

    @Nested
    inner class EmptyInputTest {
        @Test
        fun `빈 리스트 처리`() {
            val category = NonLifeCategory(1, "장기보장성", "PB14", "상해")
            val result = NonLifeJsonParser.toProductRawData(emptyList(), category, testChecksum)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `TP_CODE 빈 문자열은 제외`() {
            val items =
                listOf(
                    mapOf<String, Any>(
                        "TP_CODE" to "",
                        "P_CODE_NM" to "삼성화재",
                        "TP_NAME" to "화재보험",
                    ),
                )

            val category = NonLifeCategory(1, "장기보장성", "PB14", "상해")
            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `알 수 없는 tabType은 빈 리스트 반환`() {
            val items =
                listOf(
                    mapOf<String, Any>("TP_CODE" to "T001", "P_CODE_NM" to "회사", "TP_NAME" to "상품"),
                )
            val category = NonLifeCategory(99, "기타", "XX99", "기타")
            val result = NonLifeJsonParser.toProductRawData(items, category, testChecksum)
            assertTrue(result.isEmpty())
        }
    }
}
