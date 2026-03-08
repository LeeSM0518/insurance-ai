package com.insuranceai.policy.entity

import com.insuranceai.policy.domain.InsuranceType
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("insurance_companies")
data class InsuranceCompanyEntity(
    @Id
    @Column("id")
    @get:JvmName("insuranceCompanyId")
    val id: UUID? = null,
    val name: String,
    val type: String,
    val associationCode: String? = null,
    val phone: String? = null,
    val aliases: List<String> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null

    fun typeEnum(): InsuranceType = InsuranceType.valueOf(type)
}
