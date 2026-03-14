package com.insuranceai.evaluation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.UUID

@ConfigurationProperties(prefix = "app.eval")
data class EvalConfig(
    val mode: EvalMode = EvalMode.EVALUATE,
    val datasetId: UUID? = null,
    val datasetName: String? = null,
    val count: Int = 90,
    val outputPath: String = "golden-dataset-export.json",
    val inputPath: String? = null,
    /** 프롬프트 버전: v1(조문 중심) 또는 v2(설계사 실무 시나리오 중심) */
    val promptVersion: String = "v2",
)

enum class EvalMode {
    GENERATE,
    VALIDATE,
    EXPORT,
    IMPORT,
    EVALUATE,
}
