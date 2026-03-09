package com.insuranceai.auth.filter

import com.insuranceai.auth.repository.UserRepository
import com.insuranceai.auth.service.TokenService
import com.insuranceai.common.security.UserPrincipal
import kotlinx.coroutines.reactor.mono
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class JwtAuthenticationFilter(
    private val tokenService: TokenService,
    private val userRepository: UserRepository,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val token = extractToken(exchange) ?: return chain.filter(exchange)

        return mono {
            if (!tokenService.validateAccessToken(token)) return@mono null
            val userId = tokenService.getUserIdFromAccessToken(token)
            val user = userRepository.findById(userId) ?: return@mono null
            UsernamePasswordAuthenticationToken(
                UserPrincipal(user.id!!, user.nickname),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
        }.flatMap { authentication ->
            if (authentication != null) {
                chain
                    .filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            } else {
                chain.filter(exchange)
            }
        }
    }

    private fun extractToken(exchange: ServerWebExchange): String? {
        val authHeader = exchange.request.headers.getFirst("Authorization") ?: return null
        return if (authHeader.startsWith("Bearer ")) authHeader.substring(7) else null
    }
}
