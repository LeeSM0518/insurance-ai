package com.insuranceai.evaluation.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("golden_eval_results")
data class GoldenEvalResultEntity(
    @Id
    @Column("id")
    @get:JvmName("goldenEvalResultId")
    val id: UUID? = null,
    val datasetId: UUID,
    val qaPairId: UUID,
    val evalRunId: String,
    val evalType: String,
    // 검색 평가
    val retrievedArticleIds: List<UUID> = emptyList(),
    val contextPrecision: BigDecimal? = null,
    val contextRecall: BigDecimal? = null,
    val mrr: BigDecimal? = null,
    val ndcg: BigDecimal? = null,
    // 생성 평가 (규칙 기반)
    val mustIncludePass: Boolean? = null,
    val mustNotIncludePass: Boolean? = null,
    val sourceMatchPass: Boolean? = null,
    // 생성 평가 (LLM-as-Judge)
    val faithfulnessScore: BigDecimal? = null,
    val accuracyScore: BigDecimal? = null,
    val completenessScore: BigDecimal? = null,
    val sourceCitationScore: BigDecimal? = null,
    val weightedTotal: BigDecimal? = null,
    val hallucinationDetected: Boolean? = null,
    val hallucinationDetails: String? = null,
    // AI 응답 원문
    val actualAnswer: String? = null,
    // 메타
    val modelVersion: String? = null,
    val promptVersion: String? = null,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
