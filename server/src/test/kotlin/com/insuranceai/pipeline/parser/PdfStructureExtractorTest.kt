package com.insuranceai.pipeline.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfStructureExtractorTest {
    @Test
    fun `키워드가 포함된 페이지만 필터링한다`() {
        val pages =
            listOf(
                PageExtractionResult(1, "보험 상품 안내서", false, PageQualityMetrics(100, 0.5f, 20f, 30)),
                PageExtractionResult(2, "면책사항에 대한 내용", false, PageQualityMetrics(100, 0.5f, 20f, 30)),
                PageExtractionResult(3, "감액기간 안내", false, PageQualityMetrics(100, 0.5f, 20f, 30)),
                PageExtractionResult(4, "회사 소개", false, PageQualityMetrics(100, 0.5f, 20f, 30)),
            )

        val extractor =
            PdfStructureExtractor(
                suspendingChatClient = io.mockk.mockk(),
                promptLoader = io.mockk.mockk(),
            )
        val filtered = extractor.filterRelevantPages(pages)

        assertEquals(2, filtered.size)
        assertEquals(2, filtered[0].pageNumber)
        assertEquals(3, filtered[1].pageNumber)
    }

    @Test
    fun `관련 페이지가 없으면 전체 페이지를 반환한다`() {
        val pages =
            listOf(
                PageExtractionResult(1, "일반 텍스트", false, PageQualityMetrics(100, 0.5f, 20f, 30)),
                PageExtractionResult(2, "다른 텍스트", false, PageQualityMetrics(100, 0.5f, 20f, 30)),
            )

        val extractor =
            PdfStructureExtractor(
                suspendingChatClient = io.mockk.mockk(),
                promptLoader = io.mockk.mockk(),
            )
        val filtered = extractor.filterRelevantPages(pages)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `팩트체크 - 원본에 존재하는 숫자를 찾는다`() {
        val data =
            PdfStructuredData(
                surrenderValues =
                    listOf(
                        ExtractedSurrenderValue(12, 1200000, 500000, 41.7f, 0.95f),
                    ),
            )
        val originalText = "납입보험료 1,200,000원, 해약환급금 500,000원"

        val extractor =
            PdfStructureExtractor(
                suspendingChatClient = io.mockk.mockk(),
                promptLoader = io.mockk.mockk(),
            )
        val results = extractor.factCheck(data, originalText)

        assertEquals(2, results.size)
        assertTrue(results[0].foundInOriginal, "premiumPaid(1200000)이 원본에 존재해야 함")
        assertTrue(results[1].foundInOriginal, "surrenderValue(500000)이 원본에 존재해야 함")
    }

    @Test
    fun `팩트체크 - 원본에 없는 숫자를 감지한다`() {
        val data =
            PdfStructuredData(
                surrenderValues =
                    listOf(
                        ExtractedSurrenderValue(12, 9999999, 8888888, 41.7f, 0.5f),
                    ),
            )
        val originalText = "납입보험료 1,200,000원"

        val extractor =
            PdfStructureExtractor(
                suspendingChatClient = io.mockk.mockk(),
                promptLoader = io.mockk.mockk(),
            )
        val results = extractor.factCheck(data, originalText)

        assertFalse(results[0].foundInOriginal)
        assertFalse(results[1].foundInOriginal)
    }

    @Test
    fun `신뢰도 임계값 상수가 올바르다`() {
        assertEquals(0.9f, PdfStructureExtractor.AUTO_APPROVE_THRESHOLD)
        assertEquals(0.7f, PdfStructureExtractor.REVIEW_THRESHOLD)
    }
}
