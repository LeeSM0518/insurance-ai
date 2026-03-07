package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("surrender_values")
data class SurrenderValueEntity(
    @Id
    @Column("id")
    @get:JvmName("surrenderValueId")
    val id: UUID? = null,
    val productId: UUID,
    val elapsedMonths: Int,
    val premiumPaid: Long,
    val surrenderValue: Long,
    val surrenderRate: BigDecimal,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
