package com.insuranceai.common.integration

import org.slf4j.LoggerFactory
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/**
 * Testcontainers PostgreSQL 컨테이너를 테스트 스위트 전체에서 1개만 공유합니다.
 * R2DBC(앱 런타임) + JDBC(Flyway 마이그레이션) URL을 동적으로 주입합니다.
 */
class PostgresqlInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    companion object {
        private val log = LoggerFactory.getLogger(PostgresqlInitializer::class.java)

        private val postgres =
            PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg17"),
            ).withDatabaseName("insurance_ai_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("init-test.sql")

        init {
            postgres.start()
            createBigmStub()
        }

        /**
         * pg_bigm 스텁 생성.
         * pg_trgm 확장의 GIN 함수를 기반으로 gin_bigm_ops operator class를 생성합니다.
         * 이를 통해 pg_bigm을 참조하는 Flyway 마이그레이션(V6)이 테스트 환경에서도 성공합니다.
         */
        private fun createBigmStub() {
            DriverManager
                .getConnection(
                    postgres.jdbcUrl,
                    postgres.username,
                    postgres.password,
                ).use { conn ->
                    conn.createStatement().use { stmt ->
                        // pg_trgm 확장 설치
                        stmt.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")

                        // pg_trgm의 GIN 함수 시그니처를 동적으로 조회하여 gin_bigm_ops 생성
                        val createSql = buildBigmOpClassSql(stmt)
                        stmt.execute(createSql)
                        log.info("pg_bigm 스텁 (gin_bigm_ops) 생성 완료")
                    }
                }
        }

        /**
         * pg_trgm의 gin_trgm_ops 정의를 기반으로 gin_bigm_ops CREATE 문을 동적 생성합니다.
         */
        private fun buildBigmOpClassSql(stmt: java.sql.Statement): String {
            // 먼저 %% 연산자 생성
            stmt.execute(
                """
                CREATE OR REPLACE FUNCTION bigm_similarity_op(text, text)
                RETURNS boolean AS ${'$'}${'$'}
                  SELECT similarity(${'$'}1, ${'$'}2) > 0.3
                ${'$'}${'$'} LANGUAGE SQL IMMUTABLE
                """.trimIndent(),
            )
            stmt.execute(
                """
                DO ${'$'}${'$'} BEGIN
                  CREATE OPERATOR %% (
                    LEFTARG = text, RIGHTARG = text,
                    FUNCTION = bigm_similarity_op, COMMUTATOR = %%
                  );
                EXCEPTION WHEN duplicate_function THEN NULL;
                END ${'$'}${'$'}
                """.trimIndent(),
            )

            // pg_trgm의 gin_trgm_ops에서 함수 정의를 조회
            val functions = mutableListOf<String>()
            val rs =
                stmt.executeQuery(
                    """
                    SELECT amprocnum, amproc::regprocedure::text
                    FROM pg_amproc
                    JOIN pg_opclass ON pg_amproc.amprocfamily = pg_opclass.opcfamily
                    WHERE pg_opclass.opcname = 'gin_trgm_ops'
                    ORDER BY amprocnum
                    """.trimIndent(),
                )
            while (rs.next()) {
                val num = rs.getInt("amprocnum")
                val funcSig = rs.getString("amproc")
                // regprocedure 형식: function_name(arg_types)
                functions.add("  FUNCTION  $num  $funcSig")
            }
            rs.close()

            return """
                CREATE OPERATOR CLASS gin_bigm_ops
                FOR TYPE text USING gin
                AS
                  OPERATOR  1  %% (text, text),
                ${functions.joinToString(",\n")}
                """.trimIndent()
        }
    }

    override fun initialize(ctx: ConfigurableApplicationContext) {
        TestPropertyValues
            .of(
                // R2DBC (앱 런타임)
                "spring.r2dbc.url=r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}",
                "spring.r2dbc.username=${postgres.username}",
                "spring.r2dbc.password=${postgres.password}",
                // JDBC (Flyway 마이그레이션)
                "spring.flyway.url=${postgres.jdbcUrl}",
                "spring.flyway.user=${postgres.username}",
                "spring.flyway.password=${postgres.password}",
                "spring.flyway.clean-disabled=false",
            ).applyTo(ctx.environment)
    }
}
