package com.insuranceai.policy.error

import com.insuranceai.common.error.ServiceError
import org.springframework.http.HttpStatus

sealed class PolicyError(
    override val message: String,
) : ServiceError() {
    override val mainCode = "POLICY"

    data object ArticleNotFound : PolicyError("약관 조문을 찾을 수 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }
}
