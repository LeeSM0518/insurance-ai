package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ExcelParserConfigTest {
    @Nested
    inner class LifeMappingsTest {
        @Test
        fun `생보 매핑이 19개이다`() {
            assertEquals(19, ExcelParserConfig.LIFE_FILE_MAPPINGS.size)
        }

        @Test
        fun `생보 파일명으로 매핑을 찾는다`() {
            val mapping = ExcelParserConfig.findMapping("life_PROTECTION_CANCER.xls")
            assertNull(mapping) // "CANCER"이 아닌 "암보험" 키워드

            val cancerMapping = ExcelParserConfig.findMapping("생보_암보험_비교.xls")
            assertNotNull(cancerMapping)
            assertEquals("PROTECTION", cancerMapping!!.category)
            assertEquals("CANCER", cancerMapping.subcategory)
        }
    }

    @Nested
    inner class NonLifeMappingsTest {
        @Test
        fun `손보 매핑이 18개이다`() {
            assertEquals(18, ExcelParserConfig.NON_LIFE_FILE_MAPPINGS.size)
        }

        @Test
        fun `손보 장기보장성 매핑이 12개이다`() {
            val protection = ExcelParserConfig.NON_LIFE_FILE_MAPPINGS.filter { it.category == "NL_PROTECTION" }
            assertEquals(12, protection.size)
        }

        @Test
        fun `손보 장기저축성 매핑이 2개이다`() {
            val savings = ExcelParserConfig.NON_LIFE_FILE_MAPPINGS.filter { it.category == "NL_SAVINGS" }
            assertEquals(2, savings.size)
        }

        @Test
        fun `손보 실손의료보험 매핑이 3개이다`() {
            val health = ExcelParserConfig.NON_LIFE_FILE_MAPPINGS.filter { it.category == "NL_HEALTH" }
            assertEquals(3, health.size)
        }

        @Test
        fun `손보 연금저축 매핑이 1개이다`() {
            val pension = ExcelParserConfig.NON_LIFE_FILE_MAPPINGS.filter { it.category == "NL_PENSION" }
            assertEquals(1, pension.size)
        }

        @Test
        fun `손보 파일명으로 매핑을 찾는다`() {
            val mapping = ExcelParserConfig.findMapping("nonlife_PB22_암.xls")
            assertNotNull(mapping)
            assertEquals("NL_PROTECTION", mapping!!.category)
            assertEquals("PB22", mapping.subcategory)
            assertEquals(ExcelParserConfig.ParserType.PROTECTION, mapping.parserType)
        }

        @Test
        fun `손보 연금저축 파일명 매핑`() {
            val mapping = ExcelParserConfig.findMapping("nonlife_PC99_연금저축.xls")
            assertNotNull(mapping)
            assertEquals("NL_PENSION", mapping!!.category)
            assertEquals("PC99", mapping.subcategory)
            assertEquals(ExcelParserConfig.ParserType.PENSION, mapping.parserType)
        }

        @Test
        fun `손보 실손의료 파일명 매핑`() {
            val mapping = ExcelParserConfig.findMapping("nonlife_PB26_실손의료보험_4세대.xls")
            assertNotNull(mapping)
            assertEquals("NL_HEALTH", mapping!!.category)
            assertEquals("PB26", mapping.subcategory)
            assertEquals(ExcelParserConfig.ParserType.HEALTH, mapping.parserType)
        }
    }

    @Nested
    inner class FindMappingByCategoryTest {
        @Test
        fun `손보 카테고리와 서브카테고리로 매핑을 찾는다`() {
            val mapping = ExcelParserConfig.findMappingByCategory("NL_PROTECTION", "PB22")
            assertNotNull(mapping)
            assertEquals(ExcelParserConfig.ParserType.PROTECTION, mapping!!.parserType)
        }

        @Test
        fun `생보 카테고리로도 여전히 찾을 수 있다`() {
            val mapping = ExcelParserConfig.findMappingByCategory("PROTECTION", "CANCER")
            assertNotNull(mapping)
            assertEquals(ExcelParserConfig.ParserType.PROTECTION, mapping!!.parserType)
        }

        @Test
        fun `존재하지 않는 카테고리는 null이다`() {
            val mapping = ExcelParserConfig.findMappingByCategory("UNKNOWN", "NONE")
            assertNull(mapping)
        }
    }

    @Nested
    inner class FileMappingsTest {
        @Test
        fun `전체 매핑은 생보 + 손보 합산이다`() {
            assertEquals(
                ExcelParserConfig.LIFE_FILE_MAPPINGS.size + ExcelParserConfig.NON_LIFE_FILE_MAPPINGS.size,
                ExcelParserConfig.FILE_MAPPINGS.size,
            )
            assertEquals(37, ExcelParserConfig.FILE_MAPPINGS.size)
        }

        @Test
        fun `손보 암보험 보장범위지수 설정이 포함되어 있다`() {
            assertTrue("PB22" in ExcelParserConfig.ProtectionOptionalColumns.HAS_COVERAGE_INDEX)
        }
    }
}
