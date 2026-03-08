package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.PolicyEmbeddingEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface PolicyEmbeddingRepository :
    CoroutineCrudRepository<PolicyEmbeddingEntity, UUID>,
    PolicyEmbeddingRepositoryCustom {
    suspend fun findByPolicyArticleId(articleId: UUID): PolicyEmbeddingEntity?
}
