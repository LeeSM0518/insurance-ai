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

    /** 여러 상품의 특정 contentType 조문 일괄 조회 (면책 조문 보강용) */
    fun findByProductIdInAndContentTypeAndIsActive(
        productIds: Collection<UUID>,
        contentType: String,
        isActive: Boolean = true,
    ): Flow<PolicyArticleEntity>
}
