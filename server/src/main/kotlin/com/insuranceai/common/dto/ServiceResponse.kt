package com.insuranceai.common.dto

import com.insuranceai.common.error.ServiceError

/**
 * 통합 API 응답 DTO
 * 모든 API 응답을 { success, statusCode, data?, code?, message? } 형식으로 통일
 */
data class ServiceResponse<T>(
    val success: Boolean,
    val statusCode: Int,
    val code: String? = null,
    val message: String? = null,
    val data: T? = null,
) {
    companion object {
        fun <T> ok(
            data: T,
            statusCode: Int = 200,
        ): ServiceResponse<T> =
            ServiceResponse(
                success = true,
                statusCode = statusCode,
                data = data,
            )

        fun <T> created(data: T): ServiceResponse<T> =
            ServiceResponse(
                success = true,
                statusCode = 201,
                data = data,
            )

        fun error(
            code: String,
            message: String,
            statusCode: Int,
        ): ServiceResponse<Nothing> =
            ServiceResponse(
                success = false,
                statusCode = statusCode,
                code = code,
                message = message,
            )

        fun fromServiceError(ex: ServiceError): ServiceResponse<Nothing> =
            ServiceResponse(
                success = false,
                statusCode = ex.status.value(),
                code = ex.code,
                message = ex.message,
            )
    }
}
