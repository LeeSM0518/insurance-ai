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
            every { sanitize(any()) } answers {
                val name = firstArg<String>()
                name
                    .replace(Regex("""[/\\:*?"<>|]"""), "")
                    .replace(Regex("""\s+"""), "_")
                    .take(200)
            }
            every { buildSummaryPdfPath(any(), any(), any(), any()) } answers {
                val type = args[0] as String
                val category = args[1] as String
                val company = args[2] as String
                val product = args[3] as String
                val sanitize = { name: String ->
                    name
                        .replace(Regex("""[/\\:*?"<>|]"""), "")
                        .replace(Regex("""\s+"""), "_")
                        .take(200)
                }
                val dirName = "${sanitize(company)}_${sanitize(product)}".take(200)
                Path.of("data/summaries", type, sanitize(category), "2026-03", dirName, "summary.pdf")
            }
        }
    private val config =
        PipelineConfig(
            crawl = PipelineConfig.CrawlConfig(downloadDir = "data/summaries"),
        )
    private val crawler = NonLifeSummaryPdfCrawler(crawlerCommon, config)

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
                Path.of("data/summaries/non-life/암/2026-03/메리츠화재_메리츠_암보험/summary.pdf"),
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
