package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.PremiumExampleEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface PremiumExampleRepository : CoroutineCrudRepository<PremiumExampleEntity, UUID> {
    fun findByProductId(productId: UUID): Flow<PremiumExampleEntity>

    fun findByProductIdIn(productIds: Collection<UUID>): Flow<PremiumExampleEntity>
}
