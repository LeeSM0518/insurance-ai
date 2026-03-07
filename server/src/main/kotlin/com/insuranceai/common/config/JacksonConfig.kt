package com.insuranceai.common.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Jackson 직렬화 설정
 * - camelCase 프로퍼티 네이밍
 * - 알 수 없는 프로퍼티 무시
 * - 날짜/시간 ISO 8601 문자열 포맷
 *
 * Jackson2ObjectMapperBuilderCustomizer 방식으로 Spring Boot 자동 설정(KotlinModule 등)을 유지
 */
@Configuration
class JacksonConfig {
    @Bean
    fun jacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .featuresToDisable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                ).modulesToInstall(JavaTimeModule())
        }
}
