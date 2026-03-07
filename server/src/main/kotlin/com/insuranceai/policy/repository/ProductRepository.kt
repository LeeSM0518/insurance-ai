package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface ProductRepository : CoroutineCrudRepository<ProductEntity, UUID> {
    fun findByInsuranceCompanyId(companyId: UUID): Flow<ProductEntity>

    fun findByIsActiveTrue(): Flow<ProductEntity>

    fun findByInsuranceCompanyIdAndIsActiveTrue(companyId: UUID): Flow<ProductEntity>
}
