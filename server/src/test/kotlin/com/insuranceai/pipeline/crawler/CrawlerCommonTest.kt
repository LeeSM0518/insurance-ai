package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CrawlerCommonTest {
    private val config =
        PipelineConfig(
            crawl =
                PipelineConfig.CrawlConfig(
                    delayMinMs = 100,
                    delayMaxMs = 200,
                    maxRetries = 3,
                ),
        )
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val crawlerCommon = CrawlerCommon(config, insuranceCompanyRepository)

    @Nested
    inner class CalculateChecksum {
        @Test
        fun `SHA-256 해시 생성`() {
            val result = crawlerCommon.calculateChecksum("test".toByteArray())
            assertEquals(64, result.length)
            assertTrue(result.matches(Regex("[a-f0-9]+")))
        }

        @Test
        fun `같은 입력이면 같은 해시`() {
            val hash1 = crawlerCommon.calculateChecksum("동일한 내용".toByteArray())
            val hash2 = crawlerCommon.calculateChecksum("동일한 내용".toByteArray())
            assertEquals(hash1, hash2)
        }

        @Test
        fun `다른 입력이면 다른 해시`() {
            val hash1 = crawlerCommon.calculateChecksum("내용1".toByteArray())
            val hash2 = crawlerCommon.calculateChecksum("내용2".toByteArray())
            assertNotEquals(hash1, hash2)
        }
    }

    @Nested
    inner class NormalizeCompanyName {
        @Test
        fun `(주) 제거`() {
            assertEquals("삼성생명", crawlerCommon.normalizeCompanyName("(주)삼성생명"))
        }

        @Test
        fun `㈜ 제거`() {
            assertEquals("한화생명", crawlerCommon.normalizeCompanyName("㈜한화생명"))
        }

        @Test
        fun `주식회사 제거`() {
            assertEquals("교보생명", crawlerCommon.normalizeCompanyName("주식회사교보생명"))
        }

        @Test
        fun `끝에 보험 제거`() {
            assertEquals("삼성생명", crawlerCommon.normalizeCompanyName("삼성생명보험"))
        }

        @Test
        fun `중간의 보험은 유지`() {
            assertEquals("삼성생명보험주", crawlerCommon.normalizeCompanyName("삼성생명보험주"))
        }

        @Test
        fun `이미 정규화된 이름`() {
            assertEquals("삼성생명", crawlerCommon.normalizeCompanyName("삼성생명"))
        }

        @Test
        fun `공백 트림`() {
            assertEquals("삼성생명", crawlerCommon.normalizeCompanyName("  삼성생명  "))
        }
    }

    @Nested
    inner class Sanitize {
        @Test
        fun `특수문자를 제거한다`() {
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트/파일명"))
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트:파일명"))
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트*파일명"))
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트?파일명"))
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트\"파일명"))
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트<파일명>"))
            assertEquals("테스트파일명", crawlerCommon.sanitize("테스트|파일명"))
        }

        @Test
        fun `공백을 언더스코어로 변환한다`() {
            assertEquals("한화생명_종신보험", crawlerCommon.sanitize("한화생명 종신보험"))
            assertEquals("연속_공백_테스트", crawlerCommon.sanitize("연속  공백  테스트"))
        }

        @Test
        fun `200바이트까지만 유지한다`() {
            val longName = "가".repeat(300)
            val result = crawlerCommon.sanitize(longName)
            val byteSize = result.toByteArray(Charsets.UTF_8).size
            assertTrue(byteSize <= 200, "sanitize 결과가 200바이트를 초과: ${byteSize}바이트")
        }

        @Test
        fun `슬래시를 제거한다`() {
            assertEquals("간병치매보험", crawlerCommon.sanitize("간병/치매보험"))
        }
    }

    @Nested
    inner class PathBuilders {
        @TempDir
        lateinit var tempDir: Path

        private val tempConfig =
            PipelineConfig(
                crawl = PipelineConfig.CrawlConfig(downloadDir = "PLACEHOLDER"),
            )

        private fun createCrawlerCommon(downloadDir: String): CrawlerCommon =
            CrawlerCommon(
                PipelineConfig(crawl = PipelineConfig.CrawlConfig(downloadDir = downloadDir)),
                insuranceCompanyRepository,
            )

        @Test
        fun `buildCategoryDir는 올바른 경로를 생성한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildCategoryDir("life", "종신보험")
            val yearMonth = YearMonth.now().toString()
            assertEquals(tempDir.resolve("life/종신보험/$yearMonth"), result)
            assertTrue(result.toFile().exists())
        }

        @Test
        fun `buildExcelPath는 excel 하위에 comparison_xls를 반환한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildExcelPath("life", "종신보험")
            val yearMonth = YearMonth.now().toString()
            assertEquals(tempDir.resolve("life/종신보험/$yearMonth/excel/comparison.xls"), result)
        }

        @Test
        fun `buildProductDir는 회사_상품 디렉토리를 생성한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildProductDir("life", "종신보험", "삼성생명", "삼성 더착한종신보험")
            val yearMonth = YearMonth.now().toString()
            assertEquals(tempDir.resolve("life/종신보험/$yearMonth/삼성생명_삼성_더착한종신보험"), result)
            assertTrue(result.toFile().exists())
        }

        @Test
        fun `buildSummaryPdfPath는 summary_pdf를 반환한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildSummaryPdfPath("non-life", "암", "메리츠화재", "메리츠 암보험")
            val yearMonth = YearMonth.now().toString()
            assertEquals(tempDir.resolve("non-life/암/$yearMonth/메리츠화재_메리츠_암보험/summary.pdf"), result)
        }

        @Test
        fun `buildExtractedTextPath는 extracted_txt를 반환한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildExtractedTextPath("life", "종신보험", "한화생명", "종신보험A")
            val yearMonth = YearMonth.now().toString()
            assertEquals(tempDir.resolve("life/종신보험/$yearMonth/한화생명_종신보험A/extracted.txt"), result)
        }

        @Test
        fun `buildRenewalPremiumPdfPath는 renewal-premium_pdf를 반환한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildRenewalPremiumPdfPath("life", "종신보험", "한화생명", "종신보험A")
            val yearMonth = YearMonth.now().toString()
            assertEquals(tempDir.resolve("life/종신보험/$yearMonth/한화생명_종신보험A/renewal-premium.pdf"), result)
        }

        @Test
        fun `카테고리에 특수문자가 있으면 sanitize 처리한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val result = cc.buildCategoryDir("life", "간병/치매보험")
            assertTrue(result.toString().contains("간병치매보험"))
        }

        @Test
        fun `상품 디렉토리명은 200바이트로 제한한다`() {
            val cc = createCrawlerCommon(tempDir.toString())
            val longProduct = "가".repeat(300)
            val result = cc.buildProductDir("life", "종신보험", "삼성생명", longProduct)
            val dirName = result.fileName.toString()
            val byteSize = dirName.toByteArray(Charsets.UTF_8).size
            assertTrue(byteSize <= 200, "디렉토리명이 200바이트를 초과: ${byteSize}바이트")
        }
    }

    @Nested
    inner class WithRetry {
        @Test
        fun `성공 시 바로 반환`() =
            runTest {
                var callCount = 0
                val result =
                    crawlerCommon.withRetry {
                        callCount++
                        "성공"
                    }
                assertEquals("성공", result)
                assertEquals(1, callCount)
            }

        @Test
        fun `실패 후 재시도 성공`() =
            runTest {
                var callCount = 0
                val result =
                    crawlerCommon.withRetry {
                        callCount++
                        if (callCount < 3) throw RuntimeException("실패")
                        "성공"
                    }
                assertEquals("성공", result)
                assertEquals(3, callCount)
            }

        @Test
        fun `최대 재시도 초과 시 예외`() =
            runTest {
                var callCount = 0
                try {
                    crawlerCommon.withRetry(maxRetries = 2) {
                        callCount++
                        throw RuntimeException("항상 실패")
                    }
                } catch (e: RuntimeException) {
                    assertEquals("항상 실패", e.message)
                }
                assertEquals(2, callCount)
            }
    }
}
