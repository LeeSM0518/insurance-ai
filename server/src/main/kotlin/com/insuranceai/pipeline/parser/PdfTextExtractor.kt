package com.insuranceai.pipeline.parser

import com.insuranceai.pipeline.config.PipelineConfig
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

/** PDF 페이지별 텍스트 추출 결과 */
data class PageExtractionResult(
    val pageNumber: Int,
    val text: String,
    val needsOcr: Boolean,
    val metrics: PageQualityMetrics,
)

/** 페이지 품질 메트릭 */
data class PageQualityMetrics(
    val charCount: Int,
    val koreanRatio: Float,
    val avgLineLength: Float,
    val uniqueCharCount: Int,
)

/** PDF 전체 추출 결과 */
data class PdfExtractionResult(
    val pages: List<PageExtractionResult>,
    val ocrRequiredRatio: Float,
    val totalCharCount: Int,
)

/**
 * Apache PDFBox 기반 PDF 텍스트 추출 + 페이지별 품질 판정
 */
@Component
class PdfTextExtractor(
    private val pipelineConfig: PipelineConfig,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** PDF 파일에서 페이지별 텍스트를 추출하고 품질을 판정합니다 */
    fun extract(file: File): PdfExtractionResult {
        val pages = mutableListOf<PageExtractionResult>()

        Loader.loadPDF(file).use { document ->
            val totalPages = document.numberOfPages
            log.debug("PDF 페이지 수: {}", totalPages)

            val stripper = PDFTextStripper()
            for (pageNum in 1..totalPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val text = stripper.getText(document)

                val metrics = calculateMetrics(text)
                val needsOcr = isOcrRequired(metrics)

                pages.add(
                    PageExtractionResult(
                        pageNumber = pageNum,
                        text = text,
                        needsOcr = needsOcr,
                        metrics = metrics,
                    ),
                )
            }
        }

        val ocrPages = pages.count { it.needsOcr }
        val ocrRatio = if (pages.isEmpty()) 0f else ocrPages.toFloat() / pages.size
        val totalChars = pages.sumOf { it.metrics.charCount }

        return PdfExtractionResult(
            pages = pages,
            ocrRequiredRatio = ocrRatio,
            totalCharCount = totalChars,
        )
    }

    /** 페이지 텍스트의 품질 메트릭 계산 */
    internal fun calculateMetrics(text: String): PageQualityMetrics {
        val charCount = text.length
        val koreanChars = text.count { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }
        val koreanRatio = if (charCount == 0) 0f else koreanChars.toFloat() / charCount

        val lines = text.lines().filter { it.isNotBlank() }
        val avgLineLength = if (lines.isEmpty()) 0f else lines.sumOf { it.length }.toFloat() / lines.size

        val uniqueCharCount = text.toSet().size

        return PageQualityMetrics(
            charCount = charCount,
            koreanRatio = koreanRatio,
            avgLineLength = avgLineLength,
            uniqueCharCount = uniqueCharCount,
        )
    }

    /** 품질 메트릭 기반 OCR 필요 여부 판정 */
    internal fun isOcrRequired(metrics: PageQualityMetrics): Boolean {
        val config = pipelineConfig.pdf
        return metrics.charCount < config.minCharsPerPage ||
            metrics.koreanRatio < config.minKoreanRatio ||
            metrics.avgLineLength < config.minAvgLineLength ||
            metrics.uniqueCharCount < config.minUniqueChars
    }
}
