package com.insuranceai.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * WebFlux 코덱 설정
 * - maxInMemorySize: 1MB (기본 256KB → PDF 파싱 대비 확대)
 * - CORS는 SecurityConfig(Step 4)에서 처리
 */
@Configuration
class WebFluxConfig : WebFluxConfigurer {
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
    }
}
