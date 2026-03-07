package com.insuranceai.auth.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val permitPaths: List<String> = emptyList(),
    val corsAllowedOrigins: List<String> = emptyList(),
)
