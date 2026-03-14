package com.insuranceai.evaluation.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("eval")
@EnableConfigurationProperties(EvalConfig::class)
class EvalPropertiesConfig
