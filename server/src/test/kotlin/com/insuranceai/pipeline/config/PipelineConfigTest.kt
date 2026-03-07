package com.insuranceai.pipeline.config

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PipelineConfigTest {
    @Nested
    inner class DefaultValues {
        @Test
        fun `크롤링 기본값 확인`() {
            val config = PipelineConfig()
            assertEquals(2000, config.crawl.delayMinMs)
            assertEquals(5000, config.crawl.delayMaxMs)
            assertEquals(3, config.crawl.maxRetries)
            assertEquals("InsuranceAI-Bot/1.0", config.crawl.userAgent)
            assertEquals("data/summaries", config.crawl.downloadDir)
            assertEquals(true, config.crawl.headless)
        }

        @Test
        fun `PDF 기본값 확인`() {
            val config = PipelineConfig()
            assertEquals(200, config.pdf.minCharsPerPage)
            assertEquals(0.15f, config.pdf.minKoreanRatio)
            assertEquals(8, config.pdf.minAvgLineLength)
            assertEquals(20, config.pdf.minUniqueChars)
        }
    }

    @Nested
    inner class CustomValues {
        @Test
        fun `커스텀 값 설정`() {
            val config =
                PipelineConfig(
                    crawl =
                        PipelineConfig.CrawlConfig(
                            delayMinMs = 1000,
                            delayMaxMs = 3000,
                            maxRetries = 5,
                        ),
                )
            assertEquals(1000, config.crawl.delayMinMs)
            assertEquals(3000, config.crawl.delayMaxMs)
            assertEquals(5, config.crawl.maxRetries)
        }
    }
}
