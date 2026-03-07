package com.insuranceai.pipeline.parser

/**
 * 비교공시 엑셀 파일 → (category, subcategory, 파서 유형) 매핑 설정
 */
object ExcelParserConfig {
    /** 파서 유형 (6개 카테고리별 파싱 전략) */
    enum class ParserType {
        PROTECTION,
        SAVINGS,
        VARIABLE_PROTECTION,
        VARIABLE_SAVINGS,
        PENSION,
        HEALTH,
    }

    /** 파일명 키워드 → 파서 설정 매핑 */
    data class FileParserMapping(
        val fileKeyword: String,
        val category: String,
        val subcategory: String?,
        val parserType: ParserType,
    )

    /** 생보 19개 파일 → 파서 매핑 */
    val LIFE_FILE_MAPPINGS =
        listOf(
            // 보장성 보험 (10파일)
            FileParserMapping("종신보험", "PROTECTION", "WHOLE_LIFE", ParserType.PROTECTION),
            FileParserMapping("정기보험", "PROTECTION", "TERM_LIFE", ParserType.PROTECTION),
            FileParserMapping("질병보험", "PROTECTION", "DISEASE", ParserType.PROTECTION),
            FileParserMapping("암보험", "PROTECTION", "CANCER", ParserType.PROTECTION),
            FileParserMapping("CI보험", "PROTECTION", "CI", ParserType.PROTECTION),
            FileParserMapping("상해보험", "PROTECTION", "ACCIDENT", ParserType.PROTECTION),
            FileParserMapping("어린이보험", "PROTECTION", "CHILD", ParserType.PROTECTION),
            FileParserMapping("치아보험", "PROTECTION", "DENTAL", ParserType.PROTECTION),
            FileParserMapping("간병", "PROTECTION", "NURSING", ParserType.PROTECTION),
            FileParserMapping("기타", "PROTECTION", "OTHER_PROTECTION", ParserType.PROTECTION),
            // 저축성 보험 (2파일)
            FileParserMapping("저축성_연금", "SAVINGS", "PENSION_SAVINGS", ParserType.SAVINGS),
            FileParserMapping("저축성_저축", "SAVINGS", "SAVINGS", ParserType.SAVINGS),
            // 변액보장성 (3파일)
            FileParserMapping("변액보험_보장성_종신", "VARIABLE_PROTECTION", "WHOLE_LIFE", ParserType.VARIABLE_PROTECTION),
            FileParserMapping("변액보험_보장성_정기", "VARIABLE_PROTECTION", "TERM_LIFE", ParserType.VARIABLE_PROTECTION),
            FileParserMapping("변액보험_보장성_CI", "VARIABLE_PROTECTION", "CI", ParserType.VARIABLE_PROTECTION),
            // 변액저축성 (2파일)
            FileParserMapping("변액보험_저축성_연금", "VARIABLE_SAVINGS", "PENSION_SAVINGS", ParserType.VARIABLE_SAVINGS),
            FileParserMapping("변액보험_저축성_저축", "VARIABLE_SAVINGS", "SAVINGS", ParserType.VARIABLE_SAVINGS),
            // 연금저축 (1파일)
            FileParserMapping("연금저축", "PENSION", null, ParserType.PENSION),
            // 실손의료 (1파일)
            FileParserMapping("실손의료", "HEALTH", null, ParserType.HEALTH),
        )

    /** 손보 18개 카테고리 → 파서 매핑 (파일명: nonlife_{tptyCode}_{카테고리명}.xls) */
    val NON_LIFE_FILE_MAPPINGS =
        listOf(
            // 장기보장성 (tabType=1, 12개)
            FileParserMapping("nonlife_PB11", "NL_PROTECTION", "PB11", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB12", "NL_PROTECTION", "PB12", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB13", "NL_PROTECTION", "PB13", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB24", "NL_PROTECTION", "PB24", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB25", "NL_PROTECTION", "PB25", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB16", "NL_PROTECTION", "PB16", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB22", "NL_PROTECTION", "PB22", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB14", "NL_PROTECTION", "PB14", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB15", "NL_PROTECTION", "PB15", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB18", "NL_PROTECTION", "PB18", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB27", "NL_PROTECTION", "PB27", ParserType.PROTECTION),
            FileParserMapping("nonlife_PB17", "NL_PROTECTION", "PB17", ParserType.PROTECTION),
            // 장기저축성 (tabType=2, 2개)
            FileParserMapping("nonlife_PC11", "NL_SAVINGS", "PC11", ParserType.SAVINGS),
            FileParserMapping("nonlife_PC12", "NL_SAVINGS", "PC12", ParserType.SAVINGS),
            // 실손의료보험 (tabType=3, 3개)
            FileParserMapping("nonlife_PB26", "NL_HEALTH", "PB26", ParserType.HEALTH),
            FileParserMapping("nonlife_PB21", "NL_HEALTH", "PB21", ParserType.HEALTH),
            FileParserMapping("nonlife_PB23", "NL_HEALTH", "PB23", ParserType.HEALTH),
            // 연금저축 (tabType=4, 1개)
            FileParserMapping("nonlife_PC99", "NL_PENSION", "PC99", ParserType.PENSION),
        )

    /** 전체 파서 매핑 (손보를 먼저 배치하여 구체적 키워드 우선 매칭) */
    val FILE_MAPPINGS = NON_LIFE_FILE_MAPPINGS + LIFE_FILE_MAPPINGS

    /** 파일명으로 매핑 찾기 */
    fun findMapping(fileName: String): FileParserMapping? = FILE_MAPPINGS.firstOrNull { fileName.contains(it.fileKeyword) }

    /** 카테고리명으로 매핑 찾기 (크롤러 카테고리 → 파서 유형) */
    fun findMappingByCategory(
        category: String,
        subcategory: String? = null,
    ): FileParserMapping? =
        FILE_MAPPINGS.firstOrNull {
            it.category == category && (subcategory == null || it.subcategory == subcategory)
        }

    /** 보장성 보험에서 선택적 컬럼이 존재하는 소분류 */
    object ProtectionOptionalColumns {
        /** 확정이율/공시이율/최저보증이율 컬럼이 있는 소분류 */
        val HAS_INTEREST_RATE = setOf("WHOLE_LIFE", "TERM_LIFE")

        /** 최저보증사항 컬럼이 있는 소분류 */
        val HAS_MIN_GUARANTEE = setOf("WHOLE_LIFE", "TERM_LIFE", "CI")

        /** 보장범위지수 컬럼이 있는 소분류 (생보 CANCER + 손보 PB22) */
        val HAS_COVERAGE_INDEX = setOf("CANCER", "PB22")

        /** 경도치매 관련 컬럼이 있는 소분류 */
        val HAS_MILD_DEMENTIA = setOf("NURSING", "PB16")
    }
}
