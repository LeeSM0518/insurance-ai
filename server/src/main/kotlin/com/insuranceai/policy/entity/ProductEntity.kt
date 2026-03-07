package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("products")
data class ProductEntity(
    @Id
    @Column("id")
    @get:JvmName("productId")
    val id: UUID? = null,
    val insuranceCompanyId: UUID,
    val name: String,
    val category: String,
    val subcategory: String? = null,
    val renewalType: String? = null,
    val salesChannel: String? = null,
    val minPremium: Long? = null,
    val appliedInterestRate: BigDecimal? = null,
    val declaredInterestRate: BigDecimal? = null,
    val guaranteedInterestRate: BigDecimal? = null,
    val maturityAmount: Long? = null,
    val yieldRate: BigDecimal? = null,
    val checksum: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
