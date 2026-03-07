package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.SummaryPdfInfo
import io.mockk.every
import io.mockk.mockk
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LifeSummaryPdfCrawlerTest {
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
    private val crawler = LifeSummaryPdfCrawler(crawlerCommon, config)

    @Nested
    inner class IsPdfTest {
        @Test
        fun `PDF 매직 바이트를 올바르게 감지한다`() {
            assertTrue(LifeSummaryPdfCrawler.isPdf("%PDF-1.4".toByteArray()))
            assertTrue(LifeSummaryPdfCrawler.isPdf("%PDF-1.7 some content".toByteArray()))
        }

        @Test
        fun `PDF가 아닌 바이트를 거부한다`() {
            assertFalse(LifeSummaryPdfCrawler.isPdf("<html>".toByteArray()))
            assertFalse(LifeSummaryPdfCrawler.isPdf(byteArrayOf(0, 0, 0)))
            assertFalse(LifeSummaryPdfCrawler.isPdf(byteArrayOf()))
        }
    }

    @Nested
    inner class FileNoRegexTest {
        @Test
        fun `fn_fileDown에서 fileNo와 seq를 추출한다`() {
            val match = LifeSummaryPdfCrawler.FILE_NO_REGEX.find("fn_fileDown('41658', '5')")
            assertTrue(match != null)
            assertEquals("41658", match!!.groupValues[1])
            assertEquals("5", match.groupValues[2])
        }

        @Test
        fun `공백 차이를 처리한다`() {
            val match = LifeSummaryPdfCrawler.FILE_NO_REGEX.find("fn_fileDown('12345','3')")
            assertTrue(match != null)
            assertEquals("12345", match!!.groupValues[1])
            assertEquals("3", match.groupValues[2])
        }

        @Test
        fun `매칭되지 않는 문자열은 null을 반환한다`() {
            val match = LifeSummaryPdfCrawler.FILE_NO_REGEX.find("someOtherFunction('abc')")
            assertTrue(match == null)
        }
    }

    @Nested
    inner class BuildSavePathTest {
        @Test
        fun `올바른 저장 경로를 생성한다`() {
            val info =
                SummaryPdfInfo(
                    fileNo = "41658",
                    seq = "5",
                    companyName = "한화생명",
                    productName = "간편가입 제로백H종신보험 무배당",
                    category = "종신보험",
                    majorCategory = "보장성보험",
                )

            val path = crawler.buildSavePath(info)

            assertEquals(
                Path.of("data/summaries/life/종신보험/2026-03/한화생명_간편가입_제로백H종신보험_무배당/summary.pdf"),
                path,
            )
        }

        @Test
        fun `카테고리에 슬래시가 있어도 올바르게 처리한다`() {
            val info =
                SummaryPdfInfo(
                    fileNo = "12345",
                    seq = "5",
                    companyName = "삼성생명",
                    productName = "테스트보험",
                    category = "간병/치매보험",
                    majorCategory = "보장성보험",
                )

            val path = crawler.buildSavePath(info)

            // 슬래시가 제거된 디렉토리명
            assertEquals(
                Path.of("data/summaries/life/간병치매보험/2026-03/삼성생명_테스트보험/summary.pdf"),
                path,
            )
        }
    }

    @Nested
    inner class CountPdfPagesTest {
        @TempDir
        lateinit var tempDir: Path

        /** 지정 페이지 수의 PDF 파일을 생성 */
        private fun createPdf(
            path: Path,
            pageCount: Int,
        ) {
            Files.createDirectories(path.parent)
            PDDocument().use { doc ->
                repeat(pageCount) { doc.addPage(PDPage()) }
                doc.save(path.toFile())
            }
        }

        @Test
        fun `1페이지 PDF의 페이지 수를 올바르게 반환한다`() {
            val pdfPath = tempDir.resolve("single.pdf")
            createPdf(pdfPath, 1)
            assertEquals(1, crawler.countPdfPages(pdfPath))
        }

        @Test
        fun `다수 페이지 PDF의 페이지 수를 올바르게 반환한다`() {
            val pdfPath = tempDir.resolve("multi.pdf")
            createPdf(pdfPath, 15)
            assertEquals(15, crawler.countPdfPages(pdfPath))
        }
    }

    @Nested
    inner class CategoryConfigTest {
        @Test
        fun `보장성보험 카테고리 코드가 올바르다`() {
            assertEquals("024400010001", LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES["종신보험"])
            assertEquals("024400010002", LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES["정기보험"])
            assertEquals("024400010011", LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES["기타"])
            assertEquals(10, LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES.size)
        }

        @Test
        fun `실손의료보험은 SINGLE_CATEGORIES에서 별도 처리한다`() {
            assertFalse(LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES.containsKey("실손의료보험"))
            assertEquals("024400010008", LifeSummaryPdfCrawler.SINGLE_CATEGORIES["실손의료보험"])
        }

        @Test
        fun `대분류 URL 매핑이 올바르다`() {
            assertEquals(
                "/compareDis/prodCompare/assurance/listNew.do",
                LifeSummaryPdfCrawler.MAJOR_CATEGORIES["보장성보험"],
            )
            assertEquals(
                "/compareDis/prodCompare/saving/list.do",
                LifeSummaryPdfCrawler.MAJOR_CATEGORIES["저축성보험"],
            )
            assertEquals(4, LifeSummaryPdfCrawler.MAJOR_CATEGORIES.size)
        }
    }
}
