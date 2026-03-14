package com.insuranceai.evaluation

import com.insuranceai.evaluation.config.EvalConfig
import com.insuranceai.evaluation.config.EvalMode
import com.insuranceai.evaluation.service.GoldenDatasetService
import com.insuranceai.evaluation.service.GoldenQaValidationService
import com.insuranceai.evaluation.service.OfflineEvalService
import com.insuranceai.evaluation.service.SilverGeneratorService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("eval")
class EvalRunner(
    private val config: EvalConfig,
    private val goldenDatasetService: GoldenDatasetService,
    private val silverGeneratorService: SilverGeneratorService,
    private val validationService: GoldenQaValidationService,
    private val offlineEvalService: OfflineEvalService,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runBlocking {
            logger.info("===== 골든 데이터셋 평가 시스템 =====")
            logger.info("모드: {}", config.mode)

            when (config.mode) {
                EvalMode.GENERATE -> runGenerate()
                EvalMode.VALIDATE -> runValidate()
                EvalMode.EXPORT -> runExport()
                EvalMode.IMPORT -> runImport()
                EvalMode.EVALUATE -> runEvaluate()
            }

            logger.info("===== 완료 =====")
        }
    }

    /** Silver 데이터셋 생성 */
    private suspend fun runGenerate() {
        val datasetName = config.datasetName ?: error("--app.eval.dataset-name 필수")

        logger.info("데이터셋 '{}' 생성 시작 (목표: {}개)", datasetName, config.count)

        val dataset = goldenDatasetService.createDataset(datasetName)

        // 거절 QA 10개 생성
        val rejectionCount = silverGeneratorService.generateRejectionQaPairs(dataset.id!!)
        logger.info("거절 QA {}개 생성 완료", rejectionCount)

        // LLM 기반 QA 생성
        val generatedCount = silverGeneratorService.generateSilverDataset(dataset.id, config.count)
        logger.info("LLM QA {}개 생성 완료", generatedCount)

        // total_count 갱신
        goldenDatasetService.updateTotalCount(dataset.id)

        logger.info("Silver 데이터셋 생성 완료: 총 {}개 (거절 {} + LLM {})", rejectionCount + generatedCount, rejectionCount, generatedCount)
    }

    /** 자동 검증 + Gold 승격 */
    private suspend fun runValidate() {
        val datasetId = config.datasetId ?: error("--app.eval.dataset-id 필수")

        logger.info("자동 검증 시작: {}", datasetId)

        val results = validationService.validateAndPromote(datasetId)
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }

        logger.info("검증 결과: 통과 {}개 → GOLD 승격, 실패 {}개", passed, failed)

        for (result in results.filter { !it.passed }) {
            logger.warn("검증 실패 — {}: {}", result.qaCode, result.errors.joinToString("; "))
        }
    }

    /** JSON export */
    private suspend fun runExport() {
        val datasetId = config.datasetId ?: error("--app.eval.dataset-id 필수")
        goldenDatasetService.exportToJson(datasetId, config.outputPath)
    }

    /** JSON import */
    private suspend fun runImport() {
        val inputPath = config.inputPath ?: error("--app.eval.input-path 필수")
        goldenDatasetService.importFromJson(inputPath)
    }

    /** 오프라인 평가 실행 */
    private suspend fun runEvaluate() {
        val datasetId = config.datasetId ?: error("--app.eval.dataset-id 필수")

        logger.info("오프라인 평가 시작: {}", datasetId)

        val report = offlineEvalService.evaluate(datasetId)
        offlineEvalService.printReport(report)
    }
}
