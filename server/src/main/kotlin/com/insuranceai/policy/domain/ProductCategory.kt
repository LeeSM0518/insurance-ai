package com.insuranceai.policy.domain

data class CategoryInfo(
    val code: String,
    val name: String,
    val insuranceType: InsuranceType,
    val subcategories: List<SubcategoryInfo> = emptyList(),
)

data class SubcategoryInfo(
    val code: String,
    val name: String,
)

object ProductCategories {
    val ALL: List<CategoryInfo> =
        listOf(
            // 생보 (6개 대분류)
            CategoryInfo(
                code = "PROTECTION",
                name = "보장성보험",
                insuranceType = InsuranceType.LIFE,
                subcategories =
                    listOf(
                        SubcategoryInfo("WHOLE_LIFE", "종신"),
                        SubcategoryInfo("TERM_LIFE", "정기"),
                        SubcategoryInfo("DISEASE", "질병"),
                        SubcategoryInfo("CANCER", "암"),
                        SubcategoryInfo("CI", "CI"),
                        SubcategoryInfo("ACCIDENT", "상해"),
                        SubcategoryInfo("CHILD", "어린이"),
                        SubcategoryInfo("DENTAL", "치아"),
                        SubcategoryInfo("NURSING", "간병/치매"),
                        SubcategoryInfo("OTHER_PROTECTION", "기타"),
                    ),
            ),
            CategoryInfo(code = "SAVINGS", name = "저축성보험", insuranceType = InsuranceType.LIFE),
            CategoryInfo(code = "VARIABLE_PROTECTION", name = "변액보험(보장성)", insuranceType = InsuranceType.LIFE),
            CategoryInfo(code = "VARIABLE_SAVINGS", name = "변액보험(저축성)", insuranceType = InsuranceType.LIFE),
            CategoryInfo(code = "PENSION", name = "연금저축", insuranceType = InsuranceType.LIFE),
            CategoryInfo(code = "HEALTH", name = "실손의료보험", insuranceType = InsuranceType.LIFE),
            // 손보 (4개 대분류)
            CategoryInfo(
                code = "LONG_TERM",
                name = "장기보장성보험",
                insuranceType = InsuranceType.NON_LIFE,
                subcategories =
                    listOf(
                        SubcategoryInfo("LT_ACCIDENT", "상해"),
                        SubcategoryInfo("LT_DISEASE", "질병"),
                        SubcategoryInfo("LT_CANCER", "암"),
                        SubcategoryInfo("LT_DRIVER", "운전자"),
                        SubcategoryInfo("LT_CHILD", "어린이"),
                        SubcategoryInfo("LT_FIRE", "화재"),
                        SubcategoryInfo("LT_COMPREHENSIVE", "종합"),
                        SubcategoryInfo("LT_EXPENSE", "비용"),
                        SubcategoryInfo("LT_NURSING", "간병/치매"),
                        SubcategoryInfo("LT_DENTAL", "치아"),
                        SubcategoryInfo("LT_PET", "반려동물"),
                        SubcategoryInfo("LT_OTHER", "기타"),
                    ),
            ),
            CategoryInfo(
                code = "NL_HEALTH",
                name = "실손의료보험(손보)",
                insuranceType = InsuranceType.NON_LIFE,
                subcategories =
                    listOf(
                        SubcategoryInfo("STANDARD", "표준형"),
                        SubcategoryInfo("PRE_EXISTING", "유병자형"),
                        SubcategoryInfo("SENIOR", "노후실손"),
                    ),
            ),
            CategoryInfo(code = "AUTO", name = "자동차보험", insuranceType = InsuranceType.NON_LIFE),
            CategoryInfo(code = "NL_SAVINGS", name = "저축성보험(손보)", insuranceType = InsuranceType.NON_LIFE),
        )

    fun findByInsuranceType(insuranceType: InsuranceType): List<CategoryInfo> = ALL.filter { it.insuranceType == insuranceType }
}
