package com.insuranceai.evaluation.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("golden_qa_sources")
data class GoldenQaSourceEntity(
    @Id
    @Column("id")
    @get:JvmName("goldenQaSourceId")
    val id: UUID? = null,
    val qaPairId: UUID,
    val policyArticleId: UUID,
    val insuranceCompany: String,
    val productName: String,
    val articleNumber: String? = null,
    val contentType: String? = null,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
