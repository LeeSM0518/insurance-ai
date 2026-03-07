package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.PolicyArticleEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface PolicyArticleRepository : CoroutineCrudRepository<PolicyArticleEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<PolicyArticleEntity>

    fun findByProductIdAndContentType(
        productId: UUID,
        contentType: String,
    ): Flow<PolicyArticleEntity>
}
