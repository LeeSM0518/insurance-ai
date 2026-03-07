package com.insuranceai.pipeline.parser

import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.SourceType
import com.insuranceai.policy.entity.DataSourceEntity
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

/** PDF 파싱 최종 결과 */
data class PdfParsingResult(
    val articles: List<PolicyArticleEntity>,
    val surrenderValues: List<SurrenderValueEntity>,
    val dataSource: DataSourceEntity,
    val confidence: Float,
    val factCheckResults: List<FactCheckResult>,
)

/**
 * PDF 파싱 오케스트레이터.
 * PdfTextExtractor → VisionParser(필요시) → PdfStructureExtractor 순서로 처리합니다.
 */
@Service
class PdfParsingService(
    private val pdfTextExtractor: PdfTextExtractor,
    private val visionParser: VisionParser,
    private val pdfStructureExtractor: PdfStructureExtractor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * PDF 파일을 파싱하여 Entity 목록으로 변환합니다.
     * OCR 비율에 따라 텍스트/Vision 분기 처리합니다.
     */
    suspend fun parse(
        file: File,
        productId: UUID,
        companyName: String,
        productName: String,
    ): PdfParsingResult {
        log.info("PDF 파싱 시작: {} - {} ({})", companyName, productName, file.name)

        // Phase 1: 텍스트 추출
        val extraction = pdfTextExtractor.extract(file)
        log.debug("텍스트 추출 완료: OCR 필요 비율 {}", extraction.ocrRequiredRatio)

        // Phase 2: OCR 비율에 따라 분기 처리
        val finalPages = resolvePages(file, extraction)

        // Phase 3: 구조화 추출
        val structuredData =
            pdfStructureExtractor.extract(
                pages = finalPages,
                companyName = companyName,
                productName = productName,
            )

        // Phase 4: 팩트 체크
        val combinedText = finalPages.joinToString("\n") { it.text }
        val factCheckResults = pdfStructureExtractor.factCheck(structuredData, combinedText)

        // Phase 5: Entity 변환
        val articles = toArticleEntities(structuredData, productId)
        val surrenderValues = toSurrenderValueEntities(structuredData, productId)
        val dataSource =
            DataSourceEntity(
                sourceType = SourceType.PRODUCT_SUMMARY.name,
                filePath = file.absolutePath,
                checksum = calculateChecksum(file.readBytes()),
            )

        log.info(
            "PDF 파싱 완료: {} - {}, 조문 {}건, 해약환급금 {}건, 신뢰도 {}",
            companyName,
            productName,
            articles.size,
            surrenderValues.size,
            structuredData.overallConfidence,
        )

        return PdfParsingResult(
            articles = articles,
            surrenderValues = surrenderValues,
            dataSource = dataSource,
            confidence = structuredData.overallConfidence,
            factCheckResults = factCheckResults,
        )
    }

    /** OCR 비율에 따라 페이지별 텍스트를 최종 확정 */
    private suspend fun resolvePages(
        file: File,
        extraction: PdfExtractionResult,
    ): List<PageExtractionResult> {
        val ocrRatio = extraction.ocrRequiredRatio

        return when {
            // 10% 미만: 텍스트 결과 그대로 사용
            ocrRatio < 0.1f -> {
                extraction.pages
            }

            // 50% 초과: 전체 Vision 처리
            ocrRatio > 0.5f -> {
                log.info("OCR 비율 {}%: 전체 Vision API 처리", (ocrRatio * 100).toInt())
                val allPageNumbers = extraction.pages.map { it.pageNumber }
                val visionTexts = visionParser.extractPages(file, allPageNumbers)
                extraction.pages.map { page ->
                    page.copy(
                        text = visionTexts[page.pageNumber] ?: page.text,
                        needsOcr = false,
                    )
                }
            }

            // 10~50%: 선택적 처리
            else -> {
                val ocrPageNumbers = extraction.pages.filter { it.needsOcr }.map { it.pageNumber }
                log.info("OCR 비율 {}%: {}개 페이지 선택적 Vision 처리", (ocrRatio * 100).toInt(), ocrPageNumbers.size)
                val visionTexts = visionParser.extractPages(file, ocrPageNumbers)
                extraction.pages.map { page ->
                    if (page.needsOcr && visionTexts.containsKey(page.pageNumber)) {
                        page.copy(
                            text = visionTexts[page.pageNumber]!!,
                            needsOcr = false,
                        )
                    } else {
                        page
                    }
                }
            }
        }
    }

    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    /** 구조화 데이터를 PolicyArticleEntity 목록으로 변환 */
    internal fun toArticleEntities(
        data: PdfStructuredData,
        productId: UUID,
    ): List<PolicyArticleEntity> {
        val articles = mutableListOf<PolicyArticleEntity>()

        fun addItems(
            items: List<ExtractedItem>,
            contentType: ContentType,
        ) {
            items.forEach { item ->
                articles.add(
                    PolicyArticleEntity(
                        productId = productId,
                        content = item.content,
                        contentType = contentType.name,
                        sectionType = item.sectionType,
                    ),
                )
            }
        }

        addItems(data.exemptions, ContentType.EXEMPTION)
        addItems(data.reductions, ContentType.REDUCTION)
        addItems(data.qualifications, ContentType.QUALIFICATION)
        addItems(data.coverageDetails, ContentType.COVERAGE_DETAIL)
        addItems(data.claimDocuments, ContentType.CLAIM_DOCUMENTS)
        addItems(data.qna, ContentType.QNA)

        return articles
    }

    /** 구조화 데이터를 SurrenderValueEntity 목록으로 변환 */
    internal fun toSurrenderValueEntities(
        data: PdfStructuredData,
        productId: UUID,
    ): List<SurrenderValueEntity> =
        data.surrenderValues.map { sv ->
            SurrenderValueEntity(
                productId = productId,
                elapsedMonths = sv.elapsedMonths,
                premiumPaid = sv.premiumPaid,
                surrenderValue = sv.surrenderValue,
                surrenderRate = BigDecimal.valueOf(sv.surrenderRate.toDouble()),
            )
        }
}
