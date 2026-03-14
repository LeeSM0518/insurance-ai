package com.insuranceai.common.integration

import com.insuranceai.auth.entity.UserEntity
import com.insuranceai.auth.repository.UserRepository
import com.insuranceai.auth.service.TokenService
import com.insuranceai.common.fixture.UserFixtures
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

/**
 * 통합 테스트 베이스 클래스.
 * - Testcontainers PostgreSQL (pgvector:pg17)
 * - TRUNCATE CASCADE로 테스트 격리 (스키마 보존)
 * - 테스트 사용자 + JWT 자동 생성
 * - AI API 모킹 (ChatModel, EmbeddingModel)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = [PostgresqlInitializer::class])
@Import(TestSpringAiConfig::class)
abstract class BaseIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    protected lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var flyway: Flyway

    @Autowired
    lateinit var tokenService: TokenService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var databaseClient: DatabaseClient

    // 매 테스트마다 초기화되는 테스트 사용자 및 토큰
    protected lateinit var testUser: UserEntity
    protected lateinit var testAccessToken: String

    @BeforeEach
    fun baseSetUp() {
        // WebTestClient: 실제 HTTP 바인딩
        webTestClient =
            WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:$port")
                .responseTimeout(Duration.ofSeconds(30))
                .codecs { it.defaultCodecs().maxInMemorySize(4 * 1024 * 1024) }
                .build()

        // 테스트 사용자 생성 + JWT 발급
        runBlocking {
            testUser = userRepository.save(UserFixtures.entity())
            testAccessToken = tokenService.createAccessToken(testUser.id!!)
        }
    }

    @AfterEach
    fun baseTearDown() {
        // 테스트 격리: 데이터만 삭제 (스키마, 확장, 인덱스 보존)
        runBlocking {
            truncateAllTables()
        }
    }

    /** 인증 헤더 편의 메서드 */
    protected fun WebTestClient.RequestHeadersSpec<*>.withAuth(token: String = testAccessToken): WebTestClient.RequestHeadersSpec<*> =
        header("Authorization", "Bearer $token")

    /**
     * 모든 애플리케이션 테이블을 TRUNCATE CASCADE합니다.
     * flyway_schema_history와 확장/인덱스는 보존됩니다.
     */
    private suspend fun truncateAllTables() {
        val tables =
            listOf(
                "chat_feedbacks",
                "chat_message_sources",
                "chat_messages",
                "chat_sessions",
                "policy_embeddings",
                "policy_articles",
                "premium_examples",
                "product_indices",
                "product_metrics",
                "surrender_values",
                "data_sources",
                "products",
                "insurance_companies",
                "refresh_tokens",
                "users",
                "crawl_history",
                "crawl_staging",
            )
        databaseClient
            .sql("TRUNCATE ${tables.joinToString(", ")} CASCADE")
            .await()
    }
}
