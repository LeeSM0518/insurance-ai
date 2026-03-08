package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.PolicyEmbeddingEntity
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PolicyEmbeddingRepositoryImpl(
    private val databaseClient: DatabaseClient,
) : PolicyEmbeddingRepositoryCustom {
    override suspend fun saveWithVector(entity: PolicyEmbeddingEntity): PolicyEmbeddingEntity {
        val id = entity.id ?: UUID.randomUUID()
        val vectorString = entity.embedding.joinToString(",", "[", "]")
        val now = entity.createdAt

        databaseClient
            .sql(
                """
                INSERT INTO policy_embeddings (id, policy_article_id, embedding, embedding_model, created_at)
                VALUES (:id, :articleId, :embedding::vector, :model, :createdAt)
                """.trimIndent(),
            ).bind("id", id)
            .bind("articleId", entity.policyArticleId)
            .bind("embedding", vectorString)
            .bind("model", entity.embeddingModel)
            .bind("createdAt", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        return entity.copy(id = id, createdAt = now)
    }
}
