package com.insuranceai.common.config

import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * jOOQ 설정: R2DBC ConnectionFactory 기반 DSLContext 빈 등록
 * 복잡한 검색 쿼리(벡터 검색, 하이브리드 검색, 복합 JOIN)에 사용
 */
@Configuration
class JooqConfig(
    private val connectionFactory: ConnectionFactory,
) {
    @Bean
    fun dslContext(): DSLContext = DSL.using(connectionFactory, SQLDialect.POSTGRES)
}
