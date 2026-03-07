package com.insuranceai.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.auth.property.SecurityProperties
import com.insuranceai.common.dto.ServiceResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val securityProperties: SecurityProperties,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeExchange { exchange ->
                securityProperties.permitPaths.forEach { path ->
                    exchange.pathMatchers(path).permitAll()
                }
                exchange.anyExchange().authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(unauthorizedEntryPoint()) }
            .headers { headers ->
                headers.frameOptions {
                    it.mode(
                        org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY,
                    )
                }
                headers.contentTypeOptions { }
            }.build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = securityProperties.corsAllowedOrigins
                allowedMethods =
                    listOf(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name(),
                    )
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private fun unauthorizedEntryPoint(): ServerAuthenticationEntryPoint =
        ServerAuthenticationEntryPoint { exchange, _ ->
            val response = exchange.response
            response.statusCode = HttpStatus.UNAUTHORIZED
            response.headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            val body =
                objectMapper.writeValueAsBytes(
                    ServiceResponse.error(
                        code = "AUTH:TOKEN_MISSING",
                        message = "인증이 필요합니다",
                        statusCode = 401,
                    ),
                )
            response.writeWith(
                Mono.just(response.bufferFactory().wrap(body)),
            )
        }
}
