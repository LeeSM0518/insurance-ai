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
        val totalTarget = config.count

        val dataset = goldenDatasetService.createDataset(datasetName)
        val existingCount = goldenDatasetService.getQaPairCount(dataset.id!!)

        if (existingCount > 0) {
            logger.info("데이터셋 '{}' 재개 (기존: {}개, 목표: {}개)", datasetName, existingCount, totalTarget)
        } else {
            logger.info("데이터셋 '{}' 새로 생성 시작 (목표: {}개)", datasetName, totalTarget)
        }

        // count를 거절/Silver 비례 배분 (거절 10%, Silver 90%)
        val rejectionTarget = (totalTarget * 10) / 100 // count=1~9 → 0, count=10 → 1
        val silverTarget = totalTarget - rejectionTarget

        logger.info("배분: 거절 {}개 + Silver {}개 = 총 {}개", rejectionTarget, silverTarget, totalTarget)

        // 거절 QA 생성 (목표 수만큼)
        val rejectionCount = silverGeneratorService.generateRejectionQaPairs(dataset.id!!, rejectionTarget)
        logger.info("거절 QA {}개 생성 완료", rejectionCount)

        // Silver QA 생성
        val generatedCount = silverGeneratorService.generateSilverDataset(dataset.id, silverTarget)
        logger.info("Silver QA {}개 생성 완료", generatedCount)

        // total_count 갱신
        goldenDatasetService.updateTotalCount(dataset.id)

        logger.info("데이터셋 생성 완료: 총 {}개 (거절 {} + Silver {})", rejectionCount + generatedCount, rejectionCount, generatedCount)
    }

    /** 자동 검증 + Gold 승격 */
    private suspend fun runValidate() {
        val datasetId = config.datasetId ?: error("--app.eval.dataset-id 필수")

        logger.info("자동 검증 시작: {}", datasetId)

        val limit = if (config.count > 0) config.count else null
        logger.info("검증 제한: {}개", limit ?: "전체")

        val results = validationService.validateAndPromote(datasetId, limit)
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

        val limit = if (config.count > 0) config.count else null
        logger.info("평가 제한: {}개", limit ?: "전체")

        val report = offlineEvalService.evaluate(datasetId, limit)
        offlineEvalService.printReport(report)
    }
}
