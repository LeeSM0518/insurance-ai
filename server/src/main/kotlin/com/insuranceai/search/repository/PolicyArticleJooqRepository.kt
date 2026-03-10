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
    private val pSubcategory = DSL.field("products.subcategory", String::class.java)
    private val pInsuranceCompanyId = DSL.field("products.insurance_company_id", UUID::class.java)

    private val icId = DSL.field("insurance_companies.id", UUID::class.java)
    private val icName = DSL.field("insurance_companies.name", String::class.java)
    private val icType = DSL.field("insurance_companies.type", String::class.java)

    private val dsId = DSL.field("data_sources.id", UUID::class.java)
    private val dsSourceType = DSL.field("data_sources.source_type", String::class.java)

    /**
     * pg_bigm LIKE 기반 키워드 검색.
     * V6 마이그레이션의 gin(content gin_bigm_ops) 인덱스를 활용합니다.
     * match_quality: 제목 매칭(0.4) + 본문 기본(0.3) + 등장 빈도(최대 0.2) + 앞부분 보너스(0.1)
     */
    suspend fun searchByKeyword(
        query: String,
        filters: SearchFilters,
        limit: Int = 30,
    ): List<KeywordSearchResult> =
        withContext(Dispatchers.IO) {
            val likePattern = "%$query%"
            val queryLower = query.lowercase()

            // match_quality 계산 필드
            val matchQualityField =
                DSL
                    .field(
                        """
                        (
                            CASE WHEN {0} ILIKE {1} THEN 0.4 ELSE 0.0 END
                            + 0.3
                            + LEAST(
                                (LENGTH(LOWER({2})) - LENGTH(REPLACE(LOWER({2}), {3}, '')))::float
                                / NULLIF(LENGTH({3}), 0) * 0.04,
                                0.2
                              )
                            + CASE WHEN POSITION({4} IN {2}) > 0
                                   AND POSITION({4} IN {2}) < LENGTH({2}) * 0.2
                                   THEN 0.1 ELSE 0.0 END
                        )
                        """.trimIndent(),
                        Float::class.java,
                        paArticleTitle,
                        DSL.inline(likePattern),
                        paContent,
                        DSL.inline(queryLower),
                        DSL.inline(query),
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
                        matchQualityField.`as`("match_quality"),
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

            // match_quality 내림차순 정렬
            val result =
                step
                    .orderBy(DSL.field("match_quality").desc())
                    .limit(limit)

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
                        matchQuality = record.get("match_quality", Float::class.java) ?: 0.3f,
                    )
                }
        }

    /**
     * 키워드 리스트 기반 AND 검색 (RAG용).
     * 각 키워드를 ILIKE AND 조건으로 결합하여 모든 키워드가 포함된 결과만 반환합니다.
     */
    suspend fun searchByKeywords(
        keywords: List<String>,
        filters: SearchFilters,
        limit: Int = 30,
    ): List<KeywordSearchResult> =
        withContext(Dispatchers.IO) {
            val primaryKeyword = keywords.first()
            val primaryLower = primaryKeyword.lowercase()

            // match_quality: 첫 번째 키워드 기준 계산
            val matchQualityField =
                DSL
                    .field(
                        """
                        (
                            CASE WHEN {0} ILIKE {1} THEN 0.4 ELSE 0.0 END
                            + 0.3
                            + LEAST(
                                (LENGTH(LOWER({2})) - LENGTH(REPLACE(LOWER({2}), {3}, '')))::float
                                / NULLIF(LENGTH({3}), 0) * 0.04,
                                0.2
                              )
                            + CASE WHEN POSITION({4} IN {2}) > 0
                                   AND POSITION({4} IN {2}) < LENGTH({2}) * 0.2
                                   THEN 0.1 ELSE 0.0 END
                        )
                        """.trimIndent(),
                        Float::class.java,
                        paArticleTitle,
                        DSL.inline("%$primaryKeyword%"),
                        paContent,
                        DSL.inline(primaryLower),
                        DSL.inline(primaryKeyword),
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
                        matchQualityField.`as`("match_quality"),
                    ).from(policyArticles)
                    .join(products)
                    .on(paProductId.eq(pId))
                    .join(insuranceCompanies)
                    .on(pInsuranceCompanyId.eq(icId))
                    .leftJoin(dataSources)
                    .on(paDataSourceId.eq(dsId))
                    .where(paIsActive.eq(true))

            // 각 키워드에 대해 AND 조건 추가
            for (keyword in keywords) {
                step = step.and(paContent.likeIgnoreCase("%$keyword%"))
            }

            // 동적 필터 적용
            step = applyFilters(step, filters)

            val result =
                step
                    .orderBy(DSL.field("match_quality").desc())
                    .limit(limit)

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
                        matchQuality = record.get("match_quality", Float::class.java) ?: 0.3f,
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
