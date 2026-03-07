package com.insuranceai.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val kakao: KakaoProperties,
    val search: SearchProperties,
    val chat: ChatProperties,
    val rateLimit: RateLimitProperties,
)

data class KakaoProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val userInfoUrl: String = "https://kapi.kakao.com/v2/user/me",
)

data class SearchProperties(
    val defaultSize: Int = 20,
    val maxSize: Int = 50,
    val minSimilarity: Float = 0.5f,
    val rrfK: Int = 60,
)

data class ChatProperties(
    val maxHistoryMessages: Int = 20,
    val maxContextTokens: Int = 16500,
    val heartbeatInterval: Duration = Duration.ofSeconds(15),
)

data class RateLimitProperties(
    val search: Int = 60,
    val chat: Int = 20,
    val auth: Int = 10,
    val global: Int = 300,
)
