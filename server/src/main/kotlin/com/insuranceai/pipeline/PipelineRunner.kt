package com.insuranceai.pipeline

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.LifeSummaryPdfCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.domain.PipelineStep
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
    private val lifeSummaryPdfCrawler: LifeSummaryPdfCrawler,
    private val config: PipelineConfig,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runBlocking {
            // SUMMARY_PDF 단독 실행 모드
            if (config.steps.size == 1 && PipelineStep.SUMMARY_PDF in config.steps) {
                runSummaryPdfDownload()
                return@runBlocking
            }

            // batchId 필수 검증: CRAWL 없이 다른 단계만 실행 시 batchId 필요
            val stepsWithoutSummary = config.steps - PipelineStep.SUMMARY_PDF
            if (stepsWithoutSummary.isNotEmpty() && PipelineStep.CRAWL !in stepsWithoutSummary && config.batchId == null) {
                logger.error("CRAWL 단계 없이 실행하려면 --app.pipeline.batch-id 옵션이 필요합니다.")
                return@runBlocking
            }

            // 카테고리 조기 검증
            if (config.categories.isNotEmpty()) {
                val validCategories =
                    when (config.type) {
                        InsuranceType.LIFE -> LifeInsuranceCrawler.CATEGORIES.map { it.displayName }
                        InsuranceType.NON_LIFE -> NonLifeInsuranceCrawler.CATEGORIES
                        null -> LifeInsuranceCrawler.CATEGORIES.map { it.displayName } + NonLifeInsuranceCrawler.CATEGORIES
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
                    steps = config.steps.toSet(),
                    batchId = config.batchId,
                )

            val activeSteps = options.resolveSteps()
            logger.info(
                "파이프라인 시작: type=${options.type ?: "ALL"}, " +
                    "categories=${options.categories.ifEmpty { listOf("ALL") }}, " +
                    "steps=$activeSteps, " +
                    "batchId=${options.batchId ?: "(신규 생성)"}, " +
                    "incremental=${options.incremental}",
            )

            val result = runPipelineUseCase.runWithOptions(options)

            logger.info(
                """
                ===== 파이프라인 실행 리포트 =====
                소요 시간: ${result.totalDuration}
                생보: 수집=${result.life.crawled}, 파싱=${result.life.parsed}, 검증=${result.life.validated}, 반영=${result.life.published}, 임베딩=${result.life.embedded}, batchId=${result.life.batchId ?: "-"}
                손보: 수집=${result.nonLife.crawled}, 파싱=${result.nonLife.parsed}, 검증=${result.nonLife.validated}, 반영=${result.nonLife.published}, 임베딩=${result.nonLife.embedded}, batchId=${result.nonLife.batchId ?: "-"}
                에러: ${(result.life.errors + result.nonLife.errors).joinToString("; ")}
                ================================
                """.trimIndent(),
            )
        }
    }

    /** 상품 요약서 PDF 다운로드 전용 실행 */
    private suspend fun runSummaryPdfDownload() {
        logger.info(
            "상품 요약서 PDF 다운로드 시작: categories=${config.categories.ifEmpty { listOf("ALL") }}, force=${config.force}",
        )

        val infos = lifeSummaryPdfCrawler.collectAll(config.categories)
        if (infos.isEmpty()) {
            logger.warn("수집된 상품 요약서 정보 0건")
            return
        }

        logger.info("수집 완료: ${infos.size}건, 다운로드 시작...")
        val report = lifeSummaryPdfCrawler.downloadAll(infos, force = config.force)

        logger.info(
            """
            ===== 상품 요약서 다운로드 리포트 =====
            총 수집: ${report.totalCollected}건
            다운로드 성공: ${report.downloaded}건
            이미 존재(스킵): ${report.skipped}건
            실패: ${report.failed}건
            카테고리별: ${report.categoryStats}
            에러: ${report.errors.take(10).joinToString("; ")}
            ====================================
            """.trimIndent(),
        )
    }
}
