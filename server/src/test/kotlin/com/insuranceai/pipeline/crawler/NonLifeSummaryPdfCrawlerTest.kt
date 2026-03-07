package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.SummaryPdfInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class NonLifeSummaryPdfCrawlerTest {
    private val crawlerCommon =
        mockk<CrawlerCommon>(relaxed = true) {
            every { buildDatedDownloadDir(*anyVararg()) } answers {
                val subPaths = args[0] as Array<*>
                Path.of("data/summaries", *subPaths.map { it.toString() }.toTypedArray(), "2026-03-07")
            }
        }
    private val config =
        PipelineConfig(
            crawl = PipelineConfig.CrawlConfig(downloadDir = "data/summaries"),
        )
    private val crawler = NonLifeSummaryPdfCrawler(crawlerCommon, config)

    @Nested
    inner class SanitizeFileNameTest {
        @Test
        fun `특수문자를 제거한다`() {
            assertEquals("테스트파일명", NonLifeSummaryPdfCrawler.sanitizeFileName("테스트/파일명"))
            assertEquals("테스트파일명", NonLifeSummaryPdfCrawler.sanitizeFileName("테스트:파일명"))
        }

        @Test
        fun `공백을 언더스코어로 변환한다`() {
            assertEquals("메리츠화재_암보험", NonLifeSummaryPdfCrawler.sanitizeFileName("메리츠화재 암보험"))
        }

        @Test
        fun `200자까지만 유지한다`() {
            val longName = "가".repeat(300)
            assertEquals(200, NonLifeSummaryPdfCrawler.sanitizeFileName(longName).length)
        }
    }

    @Nested
    inner class BuildSavePathTest {
        @Test
        fun `올바른 저장 경로를 생성한다`() {
            val info =
                SummaryPdfInfo(
                    fileNo = "87207",
                    seq = "",
                    companyName = "메리츠화재",
                    productName = "메리츠 암보험",
                    category = "암",
                    majorCategory = "장기보장성",
                )

            val path = crawler.buildSavePath(info)

            assertEquals(
                Path.of("data/summaries/non-life/암/2026-03-07/메리츠화재_메리츠_암보험_87207.pdf"),
                path,
            )
        }

        @Test
        fun `카테고리 이름의 특수문자가 처리된다`() {
            val info =
                SummaryPdfInfo(
                    fileNo = "12345",
                    seq = "",
                    companyName = "삼성화재",
                    productName = "테스트보험",
                    category = "간병_치매",
                    majorCategory = "장기보장성",
                )

            val path = crawler.buildSavePath(info)

            assertTrue(path.toString().contains("간병_치매"))
        }
    }

    @Nested
    inner class CategoryFilterTest {
        @Test
        fun `전체 카테고리 수가 올바르다`() {
            assertEquals(18, NonLifeInsuranceCrawler.CATEGORIES.size)
        }

        @Test
        fun `tptyCode로 필터링할 수 있다`() {
            val filter = listOf("PB22")
            val filtered =
                NonLifeInsuranceCrawler.CATEGORIES.filter { cat ->
                    filter.any { f ->
                        cat.categoryName.contains(f) ||
                            cat.tptyCode == f ||
                            cat.tabName == f
                    }
                }
            assertEquals(1, filtered.size)
            assertEquals("암", filtered.first().categoryName)
        }
    }
}
