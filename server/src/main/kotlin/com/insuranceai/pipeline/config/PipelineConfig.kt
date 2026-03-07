package com.insuranceai.pipeline.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.pipeline")
data class PipelineConfig(
    val crawl: CrawlConfig = CrawlConfig(),
    val pdf: PdfConfig = PdfConfig(),
) {
    data class CrawlConfig(
        val delayMinMs: Long = 2000,
        val delayMaxMs: Long = 5000,
        val maxRetries: Int = 3,
        val userAgent: String = "InsuranceAI-Bot/1.0",
        val downloadDir: String = "data/summaries",
        val headless: Boolean = true,
    )

    data class PdfConfig(
        val minCharsPerPage: Int = 200,
        val minKoreanRatio: Float = 0.15f,
        val minAvgLineLength: Int = 8,
        val minUniqueChars: Int = 20,
    )
}
