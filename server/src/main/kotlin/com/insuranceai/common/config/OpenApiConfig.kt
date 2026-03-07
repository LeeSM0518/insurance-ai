package com.insuranceai.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI(Swagger UI) 설정
 * - JWT bearerAuth 보안 스키마 전역 적용
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApiDefinition(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Insurance AI API")
                    .description("보험 약관 검색·질의응답 API")
                    .version("v1"),
            ).addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            .components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
}
