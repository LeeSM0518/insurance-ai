package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.SummaryPdfInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class LifeSummaryPdfCrawlerTest {
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
    private val crawler = LifeSummaryPdfCrawler(crawlerCommon, config)

    @Nested
    inner class SanitizeFileNameTest {
        @Test
        fun `특수문자를 제거한다`() {
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트/파일명"))
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트:파일명"))
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트*파일명"))
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트?파일명"))
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트\"파일명"))
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트<파일명>"))
            assertEquals("테스트파일명", LifeSummaryPdfCrawler.sanitizeFileName("테스트|파일명"))
        }

        @Test
        fun `공백을 언더스코어로 변환한다`() {
            assertEquals("한화생명_종신보험", LifeSummaryPdfCrawler.sanitizeFileName("한화생명 종신보험"))
            assertEquals("연속_공백_테스트", LifeSummaryPdfCrawler.sanitizeFileName("연속  공백  테스트"))
        }

        @Test
        fun `200자까지만 유지한다`() {
            val longName = "가".repeat(300)
            assertEquals(200, LifeSummaryPdfCrawler.sanitizeFileName(longName).length)
        }

        @Test
        fun `슬래시를 제거한다`() {
            assertEquals("간병치매보험", LifeSummaryPdfCrawler.sanitizeFileName("간병/치매보험"))
        }
    }

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
                Path.of("data/summaries/life/종신보험/2026-03-07/한화생명_간편가입_제로백H종신보험_무배당_41658.pdf"),
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
                Path.of("data/summaries/life/간병치매보험/2026-03-07/삼성생명_테스트보험_12345.pdf"),
                path,
            )
        }
    }

    @Nested
    inner class CategoryConfigTest {
        @Test
        fun `보장성보험 카테고리 코드가 올바르다`() {
            assertEquals("024400010001", LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES["종신보험"])
            assertEquals("024400010002", LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES["정기보험"])
            assertEquals("024400010011", LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES["기타"])
            assertEquals(11, LifeSummaryPdfCrawler.ASSURANCE_CATEGORIES.size)
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
