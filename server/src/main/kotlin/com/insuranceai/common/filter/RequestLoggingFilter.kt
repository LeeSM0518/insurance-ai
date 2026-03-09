package com.insuranceai.common.filter

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 모든 HTTP 요청/응답을 로깅하는 디버깅용 필터.
 * local 프로파일에서만 활성화됩니다.
 */
@Component
@Profile("local")
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : WebFilter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        val method = request.method
        val uri = request.uri
        val startTime = System.currentTimeMillis()

        log.debug("→ [REQ] {} {} | Headers: {}", method, uri, formatHeaders(request.headers))

        return chain.filter(exchange).doFinally {
            val elapsed = System.currentTimeMillis() - startTime
            val response = exchange.response
            val status = response.statusCode
            log.debug(
                "← [RES] {} {} | {} ({}ms) | Headers: {}",
                method,
                uri.path,
                status,
                elapsed,
                formatHeaders(response.headers),
            )
        }
    }

    private fun formatHeaders(headers: org.springframework.http.HttpHeaders): String {
        val filtered =
            headers.entries
                .filter { (key, _) -> key.lowercase() !in SENSITIVE_HEADERS }
                .joinToString(", ") { (key, values) -> "$key=${values.joinToString(",")}" }
        return "{$filtered}"
    }

    companion object {
        private val SENSITIVE_HEADERS = setOf("authorization", "cookie", "set-cookie")
    }
}
