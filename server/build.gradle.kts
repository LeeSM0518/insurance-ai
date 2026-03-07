plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.insuranceai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.2")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Database
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // jOOQ
    implementation("org.jooq:jooq:3.20.8")
    implementation("org.jooq:jooq-kotlin-coroutines:3.20.8")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Playwright (JVM) — 협회 사이트 JS 동적 렌더링
    implementation("com.microsoft.playwright:playwright:1.52.0")

    // Apache POI — 엑셀 파싱
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    // Apache PDFBox — PDF 텍스트 추출
    implementation("org.apache.pdfbox:pdfbox:3.0.4")

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine")

    // API Docs
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.15")

    // Rate Limit
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // Netty DNS resolver (macOS arm64)
    if (System.getProperty("os.name").lowercase().contains("mac") &&
        System.getProperty("os.arch").lowercase().contains("aarch64")
    ) {
        runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.100.Final:osx-aarch_64")
    }

    // Env
    implementation("io.github.cdimascio:dotenv-java:3.1.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    dependsOn("ktlintFormat")
    useJUnitPlatform()
}

// OpenAPI spec 생성 설정
openApi {
    apiDocsUrl.set("http://localhost:8888/v3/api-docs")
    outputDir.set(layout.buildDirectory.dir("openapi"))
    outputFileName.set("openapi.json")
    waitTimeInSeconds.set(60)
}

// KtLint 설정
ktlint {
    version.set("1.5.0")
    android.set(false)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/jooq/**")
    }
}
