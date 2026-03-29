package com.insuranceai.report.entity

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("reports")
data class ReportEntity(
    @Id
    @Column("id")
    @get:JvmName("reportId")
    val id: UUID? = null,
    val userId: UUID,
    val reportType: String,
    val title: String? = null,
    val content: String,
    val metadata: Json? = null,
    val shareToken: String? = null,
    val shareExpiresAt: Instant? = null,
    val chatSessionId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
