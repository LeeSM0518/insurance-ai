package com.insuranceai.pipeline.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("pipeline")
@EnableConfigurationProperties(PipelineConfig::class)
class PipelinePropertiesConfig
