package com.insuranceai.common.error

import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * WebFlux + Kotlin Coroutines 통합 이슈 방어 핸들러
 * 응답이 이미 커밋된 상태에서 발생하는 UnsupportedOperationException 등을 안전하게 처리
 */
@Component
@Order(-2)
class GlobalWebExceptionHandler : ErrorWebExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        val response = exchange.response

        if (response.isCommitted) {
            log.info(
                "Response already committed for {} {}, ignoring error: {}",
                exchange.request.method,
                exchange.request.uri.path,
                ex.message,
            )
            return Mono.empty()
        }

        if (ex is UnsupportedOperationException && ex.message == null) {
            log.debug(
                "UnsupportedOperationException with null message for {} {}, likely response already committed",
                exchange.request.method,
                exchange.request.uri.path,
            )
            return Mono.empty()
        }

        response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
        response.headers.contentType = MediaType.APPLICATION_JSON
        val errorBody =
            """{"success":false,"statusCode":500,"code":"COMMON:INTERNAL_ERROR","message":"서버 오류가 발생했습니다","data":null}"""
        val buffer = response.bufferFactory().wrap(errorBody.toByteArray())
        return response.writeWith(Mono.just(buffer))
    }
}
