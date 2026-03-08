package com.insuranceai.policy.entity

import org.springframework.data.annotation.Id
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("policy_embeddings")
data class PolicyEmbeddingEntity(
    @Id
    @Column("id")
    @get:JvmName("policyEmbeddingId")
    val id: UUID? = null,
    val policyArticleId: UUID,
    val embedding: FloatArray,
    val embeddingModel: String = "text-embedding-3-large",
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = id == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PolicyEmbeddingEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
