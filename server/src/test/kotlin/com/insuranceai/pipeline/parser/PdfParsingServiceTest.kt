package com.insuranceai.pipeline.parser

import com.insuranceai.policy.domain.ContentType
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfParsingServiceTest {
    private val pdfTextExtractor = mockk<PdfTextExtractor>()
    private val visionParser = mockk<VisionParser>()
    private val pdfStructureExtractor = mockk<PdfStructureExtractor>()
    private val service = PdfParsingService(pdfTextExtractor, visionParser, pdfStructureExtractor)

    @Test
    fun `toArticleEntities는 구조화 데이터를 Entity로 변환한다`() {
        val productId = UUID.randomUUID()
        val data =
            PdfStructuredData(
                exemptions = listOf(ExtractedItem("면책 내용", "MAIN", 0.95f)),
                reductions = listOf(ExtractedItem("감액 내용", "RIDER", 0.9f)),
                qualifications = listOf(ExtractedItem("가입 자격", "MAIN", 0.85f)),
                coverageDetails = emptyList(),
                surrenderValues = emptyList(),
                claimDocuments = listOf(ExtractedItem("청구 서류", "MAIN", 0.9f)),
                qna = listOf(ExtractedItem("Q&A 내용", "MAIN", 0.8f)),
                overallConfidence = 0.88f,
            )

        val articles = service.toArticleEntities(data, productId)

        assertEquals(5, articles.size)
        assertEquals(ContentType.EXEMPTION.name, articles[0].contentType)
        assertEquals("면책 내용", articles[0].content)
        assertEquals("MAIN", articles[0].sectionType)
        assertEquals(ContentType.REDUCTION.name, articles[1].contentType)
        assertEquals("RIDER", articles[1].sectionType)
    }

    @Test
    fun `toSurrenderValueEntities는 Float를 BigDecimal로 변환한다`() {
        val productId = UUID.randomUUID()
        val data =
            PdfStructuredData(
                surrenderValues =
                    listOf(
                        ExtractedSurrenderValue(12, 1200000, 500000, 41.7f, 0.95f),
                        ExtractedSurrenderValue(24, 2400000, 1200000, 50.0f, 0.9f),
                    ),
            )

        val entities = service.toSurrenderValueEntities(data, productId)

        assertEquals(2, entities.size)
        assertEquals(12, entities[0].elapsedMonths)
        assertEquals(1200000L, entities[0].premiumPaid)
        assertEquals(500000L, entities[0].surrenderValue)
        assertTrue(entities[0].surrenderRate.toDouble() > 41.0)
        assertTrue(entities[0].surrenderRate.toDouble() < 42.0)
    }

    @Test
    fun `빈 구조화 데이터는 빈 Entity 목록을 반환한다`() {
        val productId = UUID.randomUUID()
        val data = PdfStructuredData()

        val articles = service.toArticleEntities(data, productId)
        val surrenderValues = service.toSurrenderValueEntities(data, productId)

        assertTrue(articles.isEmpty())
        assertTrue(surrenderValues.isEmpty())
    }
}
