package com.insuranceai.pipeline.service

import com.insuranceai.common.util.TextUtil
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.parser.ExtractedItem
import com.insuranceai.pipeline.parser.ExtractedSurrenderValue
import com.insuranceai.pipeline.parser.PdfStructuredData
import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.Normalizer
import java.util.UUID

/**
 * 크롤링 데이터와 PDF 파싱 결과를 정규화하여 DB 저장용 Entity로 변환합니다.
 */
@Service
class NormalizationService {
    /** 텍스트 정규화: 유니코드 NFC + 공백 정리 + 제어문자 제거 */
    fun normalizeText(text: String): String {
        val nfcNormalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        return TextUtil.normalize(nfcNormalized)
    }

    /** ProductRawData → ProductEntity 변환 */
    fun toProductEntity(
        raw: ProductRawData,
        insuranceCompanyId: UUID,
    ): ProductEntity =
        ProductEntity(
            insuranceCompanyId = insuranceCompanyId,
            name = normalizeText(raw.productName),
            category = normalizeText(raw.category),
            subcategory = raw.subcategory?.let { normalizeText(it) },
            renewalType = raw.renewalType,
            salesChannel = raw.salesChannel,
            checksum = raw.checksum,
            minPremium = raw.premiumExamples.minOfOrNull { it.premium },
            appliedInterestRate = raw.appliedInterestRate,
            declaredInterestRate = raw.declaredInterestRate,
            guaranteedInterestRate = raw.guaranteedInterestRate,
            expenseRatio = raw.expenseRatio,
            surrenderType = raw.surrenderType,
            productType = raw.productType,
            salesStartDate = raw.salesStartDate,
            isUniversal = raw.isUniversal,
            notes = raw.notes?.let { normalizeText(it) },
        )

    /** PdfStructuredData → PolicyArticleEntity 목록 변환 */
    fun toArticleEntities(
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
                        content = normalizeText(item.content),
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

    /** ExtractedSurrenderValue 목록 → SurrenderValueEntity 목록 변환 */
    fun toSurrenderValueEntities(
        values: List<ExtractedSurrenderValue>,
        productId: UUID,
    ): List<SurrenderValueEntity> =
        values.map { sv ->
            SurrenderValueEntity(
                productId = productId,
                elapsedMonths = sv.elapsedMonths,
                premiumPaid = sv.premiumPaid,
                surrenderValue = sv.surrenderValue,
                surrenderRate = BigDecimal.valueOf(sv.surrenderRate.toDouble()),
            )
        }
}
