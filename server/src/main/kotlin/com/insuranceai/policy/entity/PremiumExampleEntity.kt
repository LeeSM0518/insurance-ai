package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("premium_examples")
data class PremiumExampleEntity(
    @Id
    @Column("id")
    @get:JvmName("premiumExampleId")
    val id: UUID? = null,
    val productId: UUID,
    val gender: String,
    val age: Int,
    val insurancePeriod: String,
    val paymentPeriod: String,
    val insuranceAmount: Long,
    val premium: Long,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null
}
