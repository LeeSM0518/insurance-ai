package com.insuranceai.pipeline.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("crawl_staging")
data class CrawlStagingEntity(
    @Id
    @Column("id")
    @get:JvmName("crawlStagingId")
    val id: UUID? = null,
    val batchId: String,
    val entityType: String,
    val entityData: Json,
    val validationStatus: String = "PENDING",
    val validationErrors: String? = null,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
