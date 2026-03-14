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
)

enum class EvalMode {
    GENERATE,
    VALIDATE,
    EXPORT,
    IMPORT,
    EVALUATE,
}
