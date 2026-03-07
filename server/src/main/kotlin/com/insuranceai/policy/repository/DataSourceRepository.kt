package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.DataSourceEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface DataSourceRepository : CoroutineCrudRepository<DataSourceEntity, UUID> {
    suspend fun findByChecksum(checksum: String): DataSourceEntity?
}
