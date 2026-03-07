package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("product_metrics")
data class ProductMetricEntity(
    @Id
    @Column("id")
    @get:JvmName("productMetricId")
    val id: UUID? = null,
    val productId: UUID,
    val metricType: String,
    val metricValue: BigDecimal,
    val referenceDate: LocalDate,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
