package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
