package com.insuranceai.pipeline.parser

import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.slf4j.LoggerFactory
import org.springframework.ai.content.Media
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Claude Vision API로 OCR이 필요한 PDF 페이지를 처리합니다.
 * PDFBox PDFRenderer로 페이지를 PNG 이미지로 변환 후 LLM에 전송합니다.
 */
@Component
class VisionParser(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** PDF 파일의 특정 페이지를 Vision API로 텍스트 추출 */
    suspend fun extractPage(
        file: File,
        pageNumber: Int,
    ): String {
        val imageBytes = renderPageToImage(file, pageNumber)
        val media =
            Media
                .builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(imageBytes)
                .build()
        val prompt = promptLoader.load("prompts/pipeline/pdf-extraction.st")

        log.debug("Vision API로 페이지 {} 처리 중", pageNumber)
        return suspendingChatClient.callWithMedia(
            userMessage = prompt,
            media = listOf(media),
        )
    }

    /** OCR이 필요한 여러 페이지를 순차 처리 */
    suspend fun extractPages(
        file: File,
        pageNumbers: List<Int>,
    ): Map<Int, String> {
        val results = mutableMapOf<Int, String>()
        for (pageNum in pageNumbers) {
            val text = extractPage(file, pageNum)
            results[pageNum] = text
        }
        return results
    }

    /** PDF 페이지를 PNG 이미지 바이트로 변환 (300 DPI) */
    internal fun renderPageToImage(
        file: File,
        pageNumber: Int,
        dpi: Float = 300f,
    ): ByteArray {
        Loader.loadPDF(file).use { document ->
            val renderer = PDFRenderer(document)
            // PDFBox 페이지 인덱스는 0부터 시작
            val image = renderer.renderImageWithDPI(pageNumber - 1, dpi, ImageType.RGB)
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            return outputStream.toByteArray()
        }
    }
}
