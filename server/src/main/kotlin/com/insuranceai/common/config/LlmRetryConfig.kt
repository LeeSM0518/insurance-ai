package com.insuranceai.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.llm.retry")
data class LlmRetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 2000,
    val maxDelayMs: Long = 30000,
    val multiplier: Double = 2.0,
)
