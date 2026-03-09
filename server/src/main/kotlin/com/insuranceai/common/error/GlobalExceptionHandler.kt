package com.insuranceai.common.error

import com.insuranceai.common.dto.ServiceResponse
import org.slf4j.LoggerFactory
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

/**
 * 전역 예외 핸들러
 * 모든 예외를 ServiceResponse 형식으로 변환
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 비즈니스 예외 (sealed class 기반) */
    @ExceptionHandler(ServiceError::class)
    fun handleServiceError(ex: ServiceError): ResponseEntity<ServiceResponse<Nothing>> {
        log.warn("ServiceError: [${ex.code}] ${ex.message}")
        return ResponseEntity
            .status(ex.status)
            .body(ServiceResponse.fromServiceError(ex))
    }

    /** @Valid 검증 실패 */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(ex: WebExchangeBindException): ResponseEntity<ServiceResponse<Nothing>> {
        val message =
            ex.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: $message")
        return ResponseEntity
            .badRequest()
            .body(
                ServiceResponse.error(
                    code = "COMMON:VALIDATION_ERROR",
                    message = message,
                    statusCode = 400,
                ),
            )
    }

    /** JSON 파싱 오류 */
    @ExceptionHandler(DecodingException::class)
    fun handleDecoding(ex: DecodingException): ResponseEntity<ServiceResponse<Nothing>> {
        log.warn("Decoding error: ${ex.message}")
        return ResponseEntity
            .badRequest()
            .body(
                ServiceResponse.error(
                    code = "COMMON:INVALID_REQUEST_BODY",
                    message = "요청 본문을 파싱할 수 없습니다",
                    statusCode = 400,
                ),
            )
    }

    /** WebFlux + Kotlin Coroutines 통합 이슈 방어 */
    @ExceptionHandler(UnsupportedOperationException::class)
    fun handleUnsupportedOperation(ex: UnsupportedOperationException): ResponseEntity<ServiceResponse<Nothing>>? {
        if (ex.message == null) {
            log.warn("UnsupportedOperationException (ReadOnlyHttpHeaders 추정) - 스택 트레이스:", ex)
            return null
        }
        log.error("Unexpected UnsupportedOperationException: ${ex.message}", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ServiceResponse.error(
                    code = "COMMON:INTERNAL_ERROR",
                    message = "서버 내부 오류가 발생했습니다",
                    statusCode = 500,
                ),
            )
    }

    /** 예상치 못한 오류 */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ServiceResponse<Nothing>> {
        log.error("Unexpected error: ${ex.message}", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ServiceResponse.error(
                    code = "COMMON:INTERNAL_ERROR",
                    message = "서버 내부 오류가 발생했습니다",
                    statusCode = 500,
                ),
            )
    }
}
