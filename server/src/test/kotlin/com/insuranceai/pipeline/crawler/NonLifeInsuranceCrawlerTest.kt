package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler.Companion.CATEGORIES
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler.Companion.buildCategoryUrl
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler.Companion.resolveParserCategory
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler.NonLifeCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NonLifeInsuranceCrawlerTest {
    @Nested
    inner class CategoryConfigTest {
        @Test
        fun `18개 카테고리가 정의되어 있다`() {
            assertEquals(18, CATEGORIES.size)
        }

        @Test
        fun `장기보장성 탭에 12개 카테고리가 있다`() {
            val tab1 = CATEGORIES.filter { it.tabType == 1 }
            assertEquals(12, tab1.size)
            assertTrue(tab1.all { it.tabName == "장기보장성" })
        }

        @Test
        fun `장기저축성 탭에 2개 카테고리가 있다`() {
            val tab2 = CATEGORIES.filter { it.tabType == 2 }
            assertEquals(2, tab2.size)
            assertTrue(tab2.all { it.tabName == "장기저축성" })
        }

        @Test
        fun `실손의료보험 탭에 3개 카테고리가 있다`() {
            val tab3 = CATEGORIES.filter { it.tabType == 3 }
            assertEquals(3, tab3.size)
            assertTrue(tab3.all { it.tabName == "실손의료보험" })
        }

        @Test
        fun `연금저축 탭에 1개 카테고리가 있다`() {
            val tab4 = CATEGORIES.filter { it.tabType == 4 }
            assertEquals(1, tab4.size)
            assertEquals("PC99", tab4.first().tptyCode)
        }

        @Test
        fun `tptyCode가 모두 고유하다`() {
            val codes = CATEGORIES.map { it.tptyCode }
            assertEquals(codes.size, codes.toSet().size)
        }
    }

    @Nested
    inner class BuildCategoryUrlTest {
        @Test
        fun `올바른 URL을 생성한다`() {
            val category = NonLifeCategory(1, "장기보장성", "PB22", "암")
            val url = buildCategoryUrl(category)
            assertEquals(
                "https://kpub.knia.or.kr/popup/disclosurePopup.do?tabType=1&tptyCode=PB22&detailYn=Y",
                url,
            )
        }

        @Test
        fun `다른 탭의 URL을 올바르게 생성한다`() {
            val category = NonLifeCategory(4, "연금저축", "PC99", "연금저축")
            val url = buildCategoryUrl(category)
            assertEquals(
                "https://kpub.knia.or.kr/popup/disclosurePopup.do?tabType=4&tptyCode=PC99&detailYn=Y",
                url,
            )
        }
    }

    @Nested
    inner class ResolveParserCategoryTest {
        @Test
        fun `tabType 1은 NL_PROTECTION이다`() {
            val category = NonLifeCategory(1, "장기보장성", "PB22", "암")
            assertEquals("NL_PROTECTION", resolveParserCategory(category))
        }

        @Test
        fun `tabType 2는 NL_SAVINGS이다`() {
            val category = NonLifeCategory(2, "장기저축성", "PC11", "장기화재_및_종합보험")
            assertEquals("NL_SAVINGS", resolveParserCategory(category))
        }

        @Test
        fun `tabType 3은 NL_HEALTH이다`() {
            val category = NonLifeCategory(3, "실손의료보험", "PB26", "실손의료보험_4세대")
            assertEquals("NL_HEALTH", resolveParserCategory(category))
        }

        @Test
        fun `tabType 4는 NL_PENSION이다`() {
            val category = NonLifeCategory(4, "연금저축", "PC99", "연금저축")
            assertEquals("NL_PENSION", resolveParserCategory(category))
        }
    }

    @Nested
    inner class CategoryFilterTest {
        @Test
        fun `tptyCode로 필터링할 수 있다`() {
            val filtered =
                CATEGORIES.filter { cat ->
                    listOf("PB22").any { filter ->
                        cat.categoryName.contains(filter) ||
                            cat.tptyCode == filter ||
                            cat.tabName == filter
                    }
                }
            assertEquals(1, filtered.size)
            assertEquals("PB22", filtered.first().tptyCode)
        }

        @Test
        fun `탭 이름으로 필터링할 수 있다`() {
            val filtered =
                CATEGORIES.filter { cat ->
                    listOf("실손의료보험").any { filter ->
                        cat.categoryName.contains(filter) ||
                            cat.tptyCode == filter ||
                            cat.tabName == filter
                    }
                }
            assertEquals(3, filtered.size)
        }

        @Test
        fun `카테고리 이름으로 필터링할 수 있다`() {
            val filtered =
                CATEGORIES.filter { cat ->
                    listOf("암").any { filter ->
                        cat.categoryName.contains(filter) ||
                            cat.tptyCode == filter ||
                            cat.tabName == filter
                    }
                }
            assertEquals(1, filtered.size)
            assertEquals("PB22", filtered.first().tptyCode)
        }
    }
}
