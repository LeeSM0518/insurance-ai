package com.insuranceai.report.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CheatsheetRequest(
    @field:NotBlank
    val category: String,
    @field:Size(min = 2, max = 5)
    val companyNames: List<String>,
    val recommendedProduct: String? = null,
    val customerAge: Int? = null,
    val customerGender: String? = null,
    val focusAreas: List<String>? = null,
    val chatSessionId: UUID? = null,
)
