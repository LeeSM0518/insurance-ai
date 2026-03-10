package com.insuranceai.policy.dto

import com.insuranceai.policy.domain.InsuranceType
import java.util.UUID

data class InsuranceCompanyResponse(
    val id: UUID,
    val name: String,
    val type: InsuranceType,
)
