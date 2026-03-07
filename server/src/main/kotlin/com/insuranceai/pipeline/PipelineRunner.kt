package com.insuranceai.pipeline

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.usecase.PipelineOptions
import com.insuranceai.pipeline.usecase.RunPipelineUseCase
import com.insuranceai.policy.domain.InsuranceType
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("pipeline")
class PipelineRunner(
    private val runPipelineUseCase: RunPipelineUseCase,
    private val config: PipelineConfig,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runBlocking {
            // 카테고리 조기 검증
            if (config.categories.isNotEmpty()) {
                val validCategories =
                    when (config.type) {
                        InsuranceType.LIFE -> LifeInsuranceCrawler.CATEGORIES
                        InsuranceType.NON_LIFE -> NonLifeInsuranceCrawler.CATEGORIES
                        null -> LifeInsuranceCrawler.CATEGORIES + NonLifeInsuranceCrawler.CATEGORIES
                    }
                val invalid = config.categories - validCategories.toSet()
                if (invalid.isNotEmpty()) {
                    logger.warn("알 수 없는 카테고리 무시: $invalid (유효: $validCategories)")
                }
            }

            val options =
                PipelineOptions(
                    type = config.type,
                    categories = config.categories,
                    incremental = config.incremental,
                    dryRun = config.dryRun,
                )

            logger.info(
                "파이프라인 시작: type=${options.type ?: "ALL"}, " +
                    "categories=${options.categories.ifEmpty { listOf("ALL") }}, " +
                    "incremental=${options.incremental}, dryRun=${options.dryRun}",
            )

            val result = runPipelineUseCase.runWithOptions(options)

            logger.info(
                """
                ===== 파이프라인 실행 리포트 =====
                소요 시간: ${result.totalDuration}
                생보: 수집=${result.life.crawled}, 파싱=${result.life.parsed}, 검증=${result.life.validated}, 반영=${result.life.published}, 임베딩=${result.life.embedded}
                손보: 수집=${result.nonLife.crawled}, 파싱=${result.nonLife.parsed}, 검증=${result.nonLife.validated}, 반영=${result.nonLife.published}, 임베딩=${result.nonLife.embedded}
                에러: ${(result.life.errors + result.nonLife.errors).joinToString("; ")}
                ================================
                """.trimIndent(),
            )
        }
    }
}
