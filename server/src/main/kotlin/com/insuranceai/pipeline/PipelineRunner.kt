package com.insuranceai.pipeline

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.LifeSummaryPdfCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeSummaryPdfCrawler
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
    private val nonLifeSummaryPdfCrawler: NonLifeSummaryPdfCrawler,
    private val config: PipelineConfig,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runBlocking {
            // SUMMARY_PDF 단독 실행 모드 (기존 독립 실행 유지)
            val pdfOnlySteps = setOf(PipelineStep.SUMMARY_PDF, PipelineStep.PARSE_PDF)
            if (config.steps.isNotEmpty() &&
                config.steps
                    .toSet()
                    .subtract(pdfOnlySteps)
                    .isEmpty() &&
                config.steps.size == 1 &&
                PipelineStep.SUMMARY_PDF in config.steps &&
                config.batchId == null
            ) {
                runSummaryPdfDownload()
                return@runBlocking
            }

            // batchId 필수 검증: CRAWL 없이 다른 단계만 실행 시 batchId 필요 (EMBED 단독은 전체 모드 허용)
            val stepsWithoutPdfOnly = config.steps - pdfOnlySteps
            val isEmbedOnly = stepsWithoutPdfOnly.toSet() == setOf(PipelineStep.EMBED)
            val isLinkPdfOnly = stepsWithoutPdfOnly.toSet() == setOf(PipelineStep.LINK_PDF)
            if (
                stepsWithoutPdfOnly.isNotEmpty() &&
                PipelineStep.CRAWL !in stepsWithoutPdfOnly &&
                config.batchId == null &&
                !isEmbedOnly &&
                !isLinkPdfOnly
            ) {
                logger.error("CRAWL 단계 없이 실행하려면 --app.pipeline.batch-id 옵션이 필요합니다.")
                return@runBlocking
            }

            // 카테고리 조기 검증
            if (config.categories.isNotEmpty()) {
                val lifeNames = LifeInsuranceCrawler.CATEGORIES.map { it.displayName }
                val nonLifeNames =
                    NonLifeInsuranceCrawler.CATEGORIES.flatMap {
                        listOf(it.categoryName, it.tptyCode, it.tabName)
                    }
                val validCategories =
                    when (config.type) {
                        InsuranceType.LIFE -> lifeNames
                        InsuranceType.NON_LIFE -> nonLifeNames
                        null -> lifeNames + nonLifeNames
                    }
                val invalid = config.categories - validCategories.toSet()
                if (invalid.isNotEmpty()) {
                    logger.warn("알 수 없는 카테고리 무시: $invalid")
                }
            }

            val options =
                PipelineOptions(
                    type = config.type,
                    categories = config.categories,
                    companies = config.companies,
                    incremental = config.incremental,
                    dryRun = config.dryRun,
                    steps = config.steps.toSet(),
                    batchId = config.batchId,
                    force = config.force,
                )

            val activeSteps = options.resolveSteps()
            logger.info(
                "파이프라인 시작: type=${options.type ?: "ALL"}, " +
                    "categories=${options.categories.ifEmpty { listOf("ALL") }}, " +
                    "companies=${options.companies.ifEmpty { listOf("ALL") }}, " +
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
            "상품 요약서 PDF 다운로드 시작: type=${config.type ?: "ALL"}, " +
                "categories=${config.categories.ifEmpty { listOf("ALL") }}, force=${config.force}",
        )

        // 생보 요약서
        if (config.type == null || config.type == InsuranceType.LIFE) {
            val lifeInfos = lifeSummaryPdfCrawler.collectAll(config.categories)
            if (lifeInfos.isNotEmpty()) {
                logger.info("생보 요약서 수집: ${lifeInfos.size}건, 다운로드 시작...")
                val report = lifeSummaryPdfCrawler.downloadAll(lifeInfos, force = config.force)
                logSummaryReport("생보", report)
            } else {
                logger.warn("생보 상품 요약서 정보 0건")
            }
        }

        // 손보 요약서
        if (config.type == null || config.type == InsuranceType.NON_LIFE) {
            val nonLifeInfos = nonLifeSummaryPdfCrawler.collectAll(config.categories)
            if (nonLifeInfos.isNotEmpty()) {
                logger.info("손보 요약서 수집: ${nonLifeInfos.size}건, 다운로드 시작...")
                val report = nonLifeSummaryPdfCrawler.downloadAll(nonLifeInfos, force = config.force)
                logSummaryReport("손보", report)
            } else {
                logger.warn("손보 상품 요약서 정보 0건")
            }
        }
    }

    private fun logSummaryReport(
        label: String,
        report: com.insuranceai.pipeline.dto.SummaryDownloadReport,
    ) {
        logger.info(
            """
            ===== $label 상품 요약서 다운로드 리포트 =====
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
