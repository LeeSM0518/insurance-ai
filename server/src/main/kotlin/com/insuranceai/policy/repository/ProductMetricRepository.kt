package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.ProductMetricEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ProductMetricRepository : CoroutineCrudRepository<ProductMetricEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<ProductMetricEntity>
}
