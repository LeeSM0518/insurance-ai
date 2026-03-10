package com.insuranceai.search.repository

import com.insuranceai.search.dto.SearchFilters
import com.insuranceai.search.dto.VectorSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * jOOQ DSL 기반 pgvector cosine distance 벡터 검색.
 * HNSW vector_cosine_ops 인덱스를 활용합니다.
 */
@Repository
class VectorSearchJooqRepository(
    private val dsl: DSLContext,
) {
    private val policyEmbeddings = DSL.table("policy_embeddings")
    private val policyArticles = DSL.table("policy_articles")
    private val products = DSL.table("products")
    private val insuranceCompanies = DSL.table("insurance_companies")
    private val dataSources = DSL.table("data_sources")

    private val peEmbedding = DSL.field("policy_embeddings.embedding")
    private val pePolicyArticleId = DSL.field("policy_embeddings.policy_article_id", UUID::class.java)

    private val paId = DSL.field("policy_articles.id", UUID::class.java)
    private val paProductId = DSL.field("policy_articles.product_id", UUID::class.java)
    private val paContent = DSL.field("policy_articles.content", String::class.java)
    private val paContentType = DSL.field("policy_articles.content_type", String::class.java)
    private val paArticleNumber = DSL.field("policy_articles.article_number", String::class.java)
    private val paArticleTitle = DSL.field("policy_articles.article_title", String::class.java)
    private val paDataSourceId = DSL.field("policy_articles.data_source_id", UUID::class.java)
    private val paIsActive = DSL.field("policy_articles.is_active", Boolean::class.java)

    private val pId = DSL.field("products.id", UUID::class.java)
    private val pName = DSL.field("products.name", String::class.java)
    private val pCategory = DSL.field("products.category", String::class.java)
    private val pSubcategory = DSL.field("products.subcategory", String::class.java)
    private val pInsuranceCompanyId = DSL.field("products.insurance_company_id", UUID::class.java)

    private val icId = DSL.field("insurance_companies.id", UUID::class.java)
    private val icName = DSL.field("insurance_companies.name", String::class.java)
    private val icType = DSL.field("insurance_companies.type", String::class.java)

    private val dsId = DSL.field("data_sources.id", UUID::class.java)
    private val dsSourceType = DSL.field("data_sources.source_type", String::class.java)

    /**
     * pgvector cosine distance 기반 유사도 검색.
     * @param queryVector 쿼리 임베딩 벡터
     * @param filters 동적 필터
     * @param limit 최대 결과 수
     * @param minSimilarity 최소 유사도 임계값
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        filters: SearchFilters,
        limit: Int = 30,
        minSimilarity: Float = 0.5f,
    ): List<VectorSearchResult> =
        withContext(Dispatchers.IO) {
            val vectorString = queryVector.joinToString(",", "[", "]")
            val similarityField =
                DSL.field(
                    "(1 - (policy_embeddings.embedding <=> {0}::vector))",
                    Float::class.java,
                    DSL.inline(vectorString),
                )

            var step =
                dsl
                    .select(
                        paId,
                        paProductId,
                        pName,
                        icId,
                        icName,
                        icType,
                        paContentType,
                        paArticleNumber,
                        paArticleTitle,
                        paContent,
                        dsSourceType,
                        similarityField.`as`("similarity"),
                    ).from(policyEmbeddings)
                    .join(policyArticles)
                    .on(pePolicyArticleId.eq(paId))
                    .join(products)
                    .on(paProductId.eq(pId))
                    .join(insuranceCompanies)
                    .on(pInsuranceCompanyId.eq(icId))
                    .leftJoin(dataSources)
                    .on(paDataSourceId.eq(dsId))
                    .where(paIsActive.eq(true))
                    .and(
                        DSL.condition(
                            "(1 - (policy_embeddings.embedding <=> {0}::vector)) >= {1}",
                            DSL.inline(vectorString),
                            DSL.inline(minSimilarity),
                        ),
                    )

            step = applyFilters(step, filters)

            val result =
                step
                    .orderBy(DSL.field("similarity").desc())
                    .limit(limit)

            Flux
                .from(result)
                .collectList()
                .awaitSingle()
                .map { record ->
                    VectorSearchResult(
                        articleId = record.get(paId)!!,
                        productId = record.get(paProductId)!!,
                        productName = record.get(pName)!!,
                        companyId = record.get(icId)!!,
                        companyName = record.get(icName)!!,
                        insuranceType = record.get(icType)!!,
                        contentType = record.get(paContentType)!!,
                        articleNumber = record.get(paArticleNumber),
                        articleTitle = record.get(paArticleTitle),
                        content = record.get(paContent)!!,
                        dataSource = record.get(dsSourceType),
                        similarity = record.get("similarity", Float::class.java) ?: 0f,
                    )
                }
        }

    /** 동적 필터 적용 */
    private fun <T : Record> applyFilters(
        step: SelectConditionStep<T>,
        filters: SearchFilters,
    ): SelectConditionStep<T> {
        var result = step

        filters.companyIds?.let { ids ->
            result = result.and(icId.`in`(ids))
        }
        filters.insuranceType?.let { type ->
            result = result.and(icType.eq(type.name))
        }
        filters.categories?.let { cats ->
            result = result.and(pCategory.`in`(cats))
        }
        filters.subcategories?.let { subs ->
            result = result.and(pSubcategory.`in`(subs))
        }
        filters.contentTypes?.let { types ->
            result = result.and(paContentType.`in`(types.map { it.name }))
        }

        return result
    }
}
