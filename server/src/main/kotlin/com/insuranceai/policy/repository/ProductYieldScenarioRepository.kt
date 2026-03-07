package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.ProductYieldScenarioEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ProductYieldScenarioRepository : CoroutineCrudRepository<ProductYieldScenarioEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<ProductYieldScenarioEntity>

    suspend fun deleteByProductId(productId: UUID)
}
