package com.insuranceai.pipeline.parser

import com.insuranceai.pipeline.config.PipelineConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfTextExtractorTest {
    private val config = PipelineConfig()
    private val extractor = PdfTextExtractor(config)

    @Nested
    inner class CalculateMetrics {
        @Test
        fun `한글 텍스트의 한글 비율을 정확히 계산한다`() {
            val text = "보험 상품 안내서입니다 ABC 123"
            val metrics = extractor.calculateMetrics(text)

            assertTrue(metrics.koreanRatio > 0.3f, "한글 비율: ${metrics.koreanRatio}")
            assertTrue(metrics.charCount > 0)
        }

        @Test
        fun `영문만 있는 텍스트는 한글 비율이 0이다`() {
            val text = "This is English text only"
            val metrics = extractor.calculateMetrics(text)

            assertEquals(0f, metrics.koreanRatio)
        }

        @Test
        fun `빈 텍스트의 메트릭은 모두 0이다`() {
            val metrics = extractor.calculateMetrics("")

            assertEquals(0, metrics.charCount)
            assertEquals(0f, metrics.koreanRatio)
            assertEquals(0f, metrics.avgLineLength)
        }

        @Test
        fun `여러 줄의 평균 줄 길이를 계산한다`() {
            val text = "첫째줄입니다\n둘째줄입니다\n셋째줄"
            val metrics = extractor.calculateMetrics(text)

            assertTrue(metrics.avgLineLength > 0f)
        }

        @Test
        fun `고유 문자 수를 정확히 계산한다`() {
            val text = "aaabbbccc"
            val metrics = extractor.calculateMetrics(text)

            assertEquals(3, metrics.uniqueCharCount)
        }
    }

    @Nested
    inner class IsOcrRequired {
        @Test
        fun `문자 수가 임계값 미만이면 OCR 필요`() {
            val metrics = PageQualityMetrics(charCount = 100, koreanRatio = 0.5f, avgLineLength = 20f, uniqueCharCount = 50)
            assertTrue(extractor.isOcrRequired(metrics))
        }

        @Test
        fun `한글 비율이 임계값 미만이면 OCR 필요`() {
            val metrics = PageQualityMetrics(charCount = 500, koreanRatio = 0.05f, avgLineLength = 20f, uniqueCharCount = 50)
            assertTrue(extractor.isOcrRequired(metrics))
        }

        @Test
        fun `평균 줄 길이가 임계값 미만이면 OCR 필요`() {
            val metrics = PageQualityMetrics(charCount = 500, koreanRatio = 0.5f, avgLineLength = 5f, uniqueCharCount = 50)
            assertTrue(extractor.isOcrRequired(metrics))
        }

        @Test
        fun `고유 문자 수가 임계값 미만이면 OCR 필요`() {
            val metrics = PageQualityMetrics(charCount = 500, koreanRatio = 0.5f, avgLineLength = 20f, uniqueCharCount = 10)
            assertTrue(extractor.isOcrRequired(metrics))
        }

        @Test
        fun `모든 메트릭이 정상이면 OCR 불필요`() {
            val metrics = PageQualityMetrics(charCount = 500, koreanRatio = 0.5f, avgLineLength = 20f, uniqueCharCount = 50)
            assertFalse(extractor.isOcrRequired(metrics))
        }
    }

    // JUnit 5 @Nested 대용
    annotation class Nested
}
