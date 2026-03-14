package com.insuranceai.common.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * 통합 테스트 인프라 검증용 스모크 테스트.
 * Testcontainers + Flyway + 인증이 정상 동작하는지 확인합니다.
 */
@Timeout(30, unit = TimeUnit.SECONDS)
class SmokeIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `앱 컨텍스트가 정상적으로 로드되고 DB 연결이 동작한다`() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `인증 없이 보호된 API 호출 시 401을 반환한다`() {
        webTestClient
            .get()
            .uri("/api/v1/users/me")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }

    @Test
    fun `인증된 사용자가 자신의 정보를 조회한다`() {
        webTestClient
            .get()
            .uri("/api/v1/users/me")
            .withAuth()
            .exchange()
            .expectStatus()
            .isOk
    }
}
