package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("product_yield_scenarios")
data class ProductYieldScenarioEntity(
    @Id
    @Column("id")
    @get:JvmName("yieldScenarioId")
    val id: UUID? = null,
    val productId: UUID,
    val scenarioType: String,
    val scenarioLabel: String,
    val yieldRate: BigDecimal,
    val gender: String? = null,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
