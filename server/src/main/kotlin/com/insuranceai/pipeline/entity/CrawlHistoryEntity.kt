package com.insuranceai.pipeline.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("crawl_history")
data class CrawlHistoryEntity(
    @Id
    @Column("id")
    @get:JvmName("crawlHistoryId")
    val id: UUID? = null,
    val batchId: String,
    val insuranceType: String,
    val totalProducts: Int,
    val successCount: Int,
    val failureCount: Int,
    val warningCount: Int = 0,
    val batchHeld: Boolean = false,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
