package com.insuranceai.evaluation.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("golden_qa_pairs")
data class GoldenQaPairEntity(
    @Id
    @Column("id")
    @get:JvmName("goldenQaPairId")
    val id: UUID? = null,
    val datasetId: UUID,
    val qaCode: String,
    val category: String,
    val questionType: String,
    val difficulty: String,
    val riskLevel: String,
    val question: String,
    val expectedAnswer: String,
    val mustInclude: List<String> = emptyList(),
    val mustNotInclude: List<String> = emptyList(),
    val sourceMustMatch: Boolean = true,
    val status: String = "SILVER",
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
