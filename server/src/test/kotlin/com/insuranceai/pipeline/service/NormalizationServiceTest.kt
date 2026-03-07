package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.parser.ExtractedItem
import com.insuranceai.pipeline.parser.ExtractedSurrenderValue
import com.insuranceai.pipeline.parser.PdfStructuredData
import com.insuranceai.policy.domain.ContentType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizationServiceTest {
    private val service = NormalizationService()

    @Test
    fun `텍스트 정규화 - 연속 공백을 단일 공백으로`() {
        assertEquals("면책 사항", service.normalizeText("면책   사항"))
    }

    @Test
    fun `텍스트 정규화 - 앞뒤 공백 제거`() {
        assertEquals("면책사항", service.normalizeText("  면책사항  "))
    }

    @Test
    fun `텍스트 정규화 - 제어 문자 제거`() {
        assertEquals("면책사항", service.normalizeText("면책\u0000사항"))
    }

    @Test
    fun `텍스트 정규화 - 유니코드 NFC 정규화`() {
        // 한글 자모 분리형 → 결합형
        val decomposed = "\u1100\u1161" // ㄱ + ㅏ → 가
        val result = service.normalizeText(decomposed)
        assertEquals("가", result)
    }

    @Test
    fun `PdfStructuredData를 ArticleEntity로 변환한다`() {
        val productId = UUID.randomUUID()
        val data =
            PdfStructuredData(
                exemptions = listOf(ExtractedItem("면책 내용", "MAIN", 0.9f)),
                qna = listOf(ExtractedItem("Q&A 내용", "MAIN", 0.8f)),
            )

        val entities = service.toArticleEntities(data, productId)

        assertEquals(2, entities.size)
        assertEquals(ContentType.EXEMPTION.name, entities[0].contentType)
        assertEquals(ContentType.QNA.name, entities[1].contentType)
        assertEquals(productId, entities[0].productId)
    }

    @Test
    fun `ExtractedSurrenderValue의 Float를 BigDecimal로 변환한다`() {
        val productId = UUID.randomUUID()
        val values =
            listOf(
                ExtractedSurrenderValue(12, 1200000, 500000, 41.7f, 0.95f),
            )

        val entities = service.toSurrenderValueEntities(values, productId)

        assertEquals(1, entities.size)
        assertEquals(12, entities[0].elapsedMonths)
        assertEquals(1200000L, entities[0].premiumPaid)
        assertTrue(entities[0].surrenderRate.toDouble() > 41.0)
        assertTrue(entities[0].surrenderRate.toDouble() < 42.0)
    }

    @Test
    fun `빈 데이터는 빈 목록을 반환한다`() {
        val productId = UUID.randomUUID()
        val data = PdfStructuredData()

        val entities = service.toArticleEntities(data, productId)
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `ProductRawData를 ProductEntity로 변환한다`() {
        val companyId = UUID.randomUUID()
        val raw =
            com.insuranceai.pipeline.dto.ProductRawData(
                companyName = "삼성생명",
                productName = "  테스트  보험  ",
                category = "종신보험",
                subcategory = "  일반  ",
                renewalType = "비갱신형",
                salesChannel = "대면",
                checksum = "abc123",
            )

        val entity = service.toProductEntity(raw, companyId)

        assertEquals("테스트 보험", entity.name) // 공백 정규화
        assertEquals("일반", entity.subcategory) // 공백 정규화
        assertEquals(companyId, entity.insuranceCompanyId)
        assertEquals("abc123", entity.checksum)
    }
}
