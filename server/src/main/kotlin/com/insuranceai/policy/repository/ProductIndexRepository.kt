package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.ProductIndexEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ProductIndexRepository : CoroutineCrudRepository<ProductIndexEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<ProductIndexEntity>

    fun findByProductIdIn(productIds: Collection<UUID>): Flow<ProductIndexEntity>
}
