package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.ProductFeatureEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ProductFeatureRepository : CoroutineCrudRepository<ProductFeatureEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<ProductFeatureEntity>

    fun findByProductIdIn(productIds: Collection<UUID>): Flow<ProductFeatureEntity>

    suspend fun deleteByProductId(productId: UUID)
}
