package com.insuranceai.common.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.PostgresDialect

/**
 * R2DBC 설정: pgvector vector(1536) ↔ FloatArray 커스텀 컨버터 등록
 *
 * ConnectionFactory는 Spring Boot가 자동 주입하므로 AbstractR2dbcConfiguration을 상속하지 않음
 */
@Configuration
class R2dbcConfig(
    private val connectionFactory: ConnectionFactory,
) {
    @Bean
    fun r2dbcCustomConversions(): R2dbcCustomConversions =
        R2dbcCustomConversions.of(
            PostgresDialect.INSTANCE,
            listOf(
                VectorReadConverter(),
                VectorWriteConverter(),
            ),
        )
}

/**
 * DB vector 타입 → FloatArray 변환
 * r2dbc-postgresql은 pgvector를 문자열 "[0.1,0.2,...]" 형태로 반환
 */
@ReadingConverter
class VectorReadConverter : Converter<String, FloatArray> {
    override fun convert(source: String): FloatArray {
        val trimmed = source.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isEmpty()) return floatArrayOf()
        return trimmed.split(",").map { it.trim().toFloat() }.toFloatArray()
    }
}

/**
 * FloatArray → pgvector 호환 문자열 변환
 * INSERT/UPDATE 시 "[0.1,0.2,...]" 형식으로 전달
 */
@WritingConverter
class VectorWriteConverter : Converter<FloatArray, String> {
    override fun convert(source: FloatArray): String = source.joinToString(",", prefix = "[", postfix = "]")
}
