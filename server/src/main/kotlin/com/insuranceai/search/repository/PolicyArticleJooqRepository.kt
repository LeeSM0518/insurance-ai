package com.insuranceai.search.repository

import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.SearchFilters
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
 * jOOQ DSL 기반 키워드 검색 쿼리.
 * pg_bigm gin(content gin_bigm_ops) 인덱스를 활용합니다.
 * Step 9에서 벡터 검색도 이 레포지토리에서 확장합니다.
 */
@Repository
class PolicyArticleJooqRepository(
    private val dsl: DSLContext,
) {
    // 문자열 기반 테이블/컬럼 참조 (jOOQ codegen 없이)
    private val policyArticles = DSL.table("policy_articles")
    private val products = DSL.table("products")
    private val insuranceCompanies = DSL.table("insurance_companies")
    private val dataSources = DSL.table("data_sources")

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
    private val pInsuranceCompanyId = DSL.field("products.insurance_company_id", UUID::class.java)

    private val icId = DSL.field("insurance_companies.id", UUID::class.java)
    private val icName = DSL.field("insurance_companies.name", String::class.java)
    private val icType = DSL.field("insurance_companies.type", String::class.java)

    private val dsId = DSL.field("data_sources.id", UUID::class.java)
    private val dsSourceType = DSL.field("data_sources.source_type", String::class.java)

    /**
     * pg_bigm LIKE 기반 키워드 검색.
     * V6 마이그레이션의 gin(content gin_bigm_ops) 인덱스를 활용합니다.
     */
    suspend fun searchByKeyword(
        query: String,
        filters: SearchFilters,
        limit: Int = 30,
    ): List<KeywordSearchResult> =
        withContext(Dispatchers.IO) {
            val likePattern = "%$query%"

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
                    ).from(policyArticles)
                    .join(products)
                    .on(paProductId.eq(pId))
                    .join(insuranceCompanies)
                    .on(pInsuranceCompanyId.eq(icId))
                    .leftJoin(dataSources)
                    .on(paDataSourceId.eq(dsId))
                    .where(paContent.likeIgnoreCase(likePattern))
                    .and(paIsActive.eq(true))

            // 동적 필터 적용
            step = applyFilters(step, filters)

            // title 매칭 우선, 그 외 content 매칭
            val result =
                step
                    .orderBy(
                        DSL
                            .`when`(
                                paArticleTitle.likeIgnoreCase(likePattern),
                                DSL.inline(0),
                            ).otherwise(DSL.inline(1)),
                    ).limit(limit)

            Flux
                .from(result)
                .collectList()
                .awaitSingle()
                .mapIndexed { index, record ->
                    KeywordSearchResult(
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
                        rank = index + 1,
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
        filters.contentTypes?.let { types ->
            result = result.and(paContentType.`in`(types.map { it.name }))
        }

        return result
    }
}
