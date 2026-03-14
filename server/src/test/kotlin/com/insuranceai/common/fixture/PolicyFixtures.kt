package com.insuranceai.common.fixture

import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.domain.SourceType
import com.insuranceai.policy.entity.DataSourceEntity
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.PolicyEmbeddingEntity
import com.insuranceai.policy.entity.ProductEntity
import java.util.UUID

/**
 * 보험 약관 관련 테스트 데이터 빌더.
 */
object PolicyFixtures {
    fun insuranceCompanyEntity(
        name: String = "삼성생명",
        type: String = InsuranceType.LIFE.name,
        associationCode: String? = "SA01",
    ) = InsuranceCompanyEntity(
        name = name,
        type = type,
        associationCode = associationCode,
    )

    fun productEntity(
        insuranceCompanyId: UUID,
        name: String = "무배당 삼성 암보험 V2.0",
        category: String = "보장성",
        subcategory: String? = "PA11",
    ) = ProductEntity(
        insuranceCompanyId = insuranceCompanyId,
        name = name,
        category = category,
        subcategory = subcategory,
    )

    fun dataSourceEntity(
        sourceType: String = SourceType.PRODUCT_SUMMARY.name,
        sourceUrl: String? = "https://example.com/summary.pdf",
        filePath: String? = "/data/life/summary.pdf",
    ) = DataSourceEntity(
        sourceType = sourceType,
        sourceUrl = sourceUrl,
        filePath = filePath,
    )

    fun policyArticleEntity(
        productId: UUID,
        dataSourceId: UUID? = null,
        articleNumber: String? = "제5조",
        articleTitle: String? = "제5조(보험금의 지급사유)",
        content: String = "이 보험의 면책기간은 계약일부터 90일입니다.",
        contentType: String = ContentType.ARTICLE.name,
        sectionType: String? = null,
    ) = PolicyArticleEntity(
        productId = productId,
        dataSourceId = dataSourceId,
        articleNumber = articleNumber,
        articleTitle = articleTitle,
        content = content,
        contentType = contentType,
        sectionType = sectionType,
    )

    fun policyEmbeddingEntity(
        policyArticleId: UUID,
        embedding: FloatArray = FloatArray(1536) { 0.01f * it },
        embeddingModel: String = "text-embedding-3-large",
    ) = PolicyEmbeddingEntity(
        policyArticleId = policyArticleId,
        embedding = embedding,
        embeddingModel = embeddingModel,
    )
}
