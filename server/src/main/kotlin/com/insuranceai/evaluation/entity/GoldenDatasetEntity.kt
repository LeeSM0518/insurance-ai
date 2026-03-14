package com.insuranceai.evaluation.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("golden_datasets")
data class GoldenDatasetEntity(
    @Id
    @Column("id")
    @get:JvmName("goldenDatasetId")
    val id: UUID? = null,
    val name: String,
    val description: String? = null,
    val version: String,
    val status: String = "DRAFT",
    val totalCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
