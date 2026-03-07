package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("product_indices")
data class ProductIndexEntity(
    @Id
    @Column("id")
    @get:JvmName("productIndexId")
    val id: UUID? = null,
    val productId: UUID,
    val indexType: String,
    val gender: String? = null,
    val coverageName: String? = null,
    val indexValue: BigDecimal,
    val baseDate: LocalDate,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
