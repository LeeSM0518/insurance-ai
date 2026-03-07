package com.insuranceai.policy.entity

import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.SectionType
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("policy_articles")
data class PolicyArticleEntity(
    @Id
    @Column("id")
    @get:JvmName("policyArticleId")
    val id: UUID? = null,
    val productId: UUID,
    val dataSourceId: UUID? = null,
    val articleNumber: String? = null,
    val articleTitle: String? = null,
    val content: String,
    val contentType: String,
    val sectionType: String? = null,
    val effectiveDate: LocalDate? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null

    fun contentTypeEnum(): ContentType = ContentType.valueOf(contentType)

    fun sectionTypeEnum(): SectionType? = sectionType?.let { SectionType.valueOf(it) }
}
