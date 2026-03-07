package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("product_features")
data class ProductFeatureEntity(
    @Id
    @Column("id")
    @get:JvmName("productFeatureId")
    val id: UUID? = null,
    val productId: UUID,
    val key: String,
    val value: String,
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
