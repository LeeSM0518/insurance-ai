package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.SurrenderValueEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface SurrenderValueRepository : CoroutineCrudRepository<SurrenderValueEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<SurrenderValueEntity>

    fun findByProductIdIn(productIds: Collection<UUID>): Flow<SurrenderValueEntity>
}
