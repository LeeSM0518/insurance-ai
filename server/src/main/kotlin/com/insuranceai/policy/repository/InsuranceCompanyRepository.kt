package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.InsuranceCompanyEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface InsuranceCompanyRepository : CoroutineCrudRepository<InsuranceCompanyEntity, UUID> {
    fun findByIsActiveTrue(): Flow<InsuranceCompanyEntity>

    fun findByType(type: String): Flow<InsuranceCompanyEntity>

    fun findByNameIn(names: Collection<String>): Flow<InsuranceCompanyEntity>
}
