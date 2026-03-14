package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("data_sources")
data class DataSourceEntity(
    @Id
    @Column("id")
    @get:JvmName("dataSourceId")
    val id: UUID? = null,
    val sourceType: String,
    val sourceUrl: String? = null,
    val filePath: String? = null,
    val crawledAt: Instant = Instant.now(),
    val checksum: String? = null,
    val extractedTextPath: String? = null,
    val productId: UUID? = null,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
