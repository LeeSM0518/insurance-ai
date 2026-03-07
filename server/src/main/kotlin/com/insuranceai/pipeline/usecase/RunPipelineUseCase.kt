package com.insuranceai.pipeline.usecase

import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.domain.PipelineStep
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.embedder.EmbeddingService
import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.pipeline.service.ChangeDetectionService
import com.insuranceai.pipeline.service.PipelineService
import com.insuranceai.pipeline.service.StagingPublisher
import com.insuranceai.pipeline.validator.CrossValidator
import com.insuranceai.pipeline.validator.ValidationService
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.ProductRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.measureTimedValue

data class PipelineOptions(
    val type: InsuranceType? = null,
    val categories: List<String> = emptyList(),
    val incremental: Boolean = false,
    val dryRun: Boolean = false,
    val steps: Set<PipelineStep> = emptySet(),
    val batchId: String? = null,
) {
    /** 실행할 단계 결정: steps 명시 > dryRun > 전체 (SUMMARY_PDF는 명시적으로만 실행) */
    fun resolveSteps(): Set<PipelineStep> =
        when {
            steps.isNotEmpty() -> steps
            dryRun -> setOf(PipelineStep.CRAWL, PipelineStep.VALIDATE)
            else -> PipelineStep.entries.toSet() - PipelineStep.SUMMARY_PDF
        }
}

data class PipelineResult(
    val life: TypePipelineResult,
    val nonLife: TypePipelineResult,
    val totalDuration: Duration,
)

data class TypePipelineResult(
    val crawled: Int,
    val parsed: Int,
    val validated: Int,
    val published: Int,
    val embedded: Int,
    val errors: List<String>,
    val batchId: String? = null,
)

@Component
class RunPipelineUseCase(
    private val lifeInsuranceCrawler: LifeInsuranceCrawler,
    private val nonLifeInsuranceCrawler: NonLifeInsuranceCrawler,
    private val changeDetectionService: ChangeDetectionService,
    private val validationService: ValidationService,
    private val crossValidator: CrossValidator,
    private val stagingPublisher: StagingPublisher,
    private val embeddingService: EmbeddingService,
    private val pipelineService: PipelineService,
    private val crawlStagingRepository: CrawlStagingRepository,
    private val crawlStagingMapper: CrawlStagingMapper,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
    private val policyArticleRepository: PolicyArticleRepository,
    private val productRepository: ProductRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 옵션 기반 파이프라인 실행 */
    suspend fun runWithOptions(options: PipelineOptions = PipelineOptions()): PipelineResult {
        val emptyResult = TypePipelineResult(0, 0, 0, 0, 0, emptyList())
        val activeSteps = options.resolveSteps()
        val typesToRun =
            when (options.type) {
                InsuranceType.LIFE -> listOf(InsuranceType.LIFE)
                InsuranceType.NON_LIFE -> listOf(InsuranceType.NON_LIFE)
                null -> listOf(InsuranceType.LIFE, InsuranceType.NON_LIFE)
            }

        val (results, duration) =
            measureTimedValue {
                typesToRun.associateWith { type ->
                    runForInsuranceType(type, options.incremental, options.categories, activeSteps, options.batchId)
                }
            }

        val pipelineResult =
            PipelineResult(
                life = results[InsuranceType.LIFE] ?: emptyResult,
                nonLife = results[InsuranceType.NON_LIFE] ?: emptyResult,
                totalDuration = duration,
            )

        logger.info(
            "파이프라인 완료: 소요시간=$duration, " +
                "생보(수집=${pipelineResult.life.crawled}, 검증=${pipelineResult.life.validated}), " +
                "손보(수집=${pipelineResult.nonLife.crawled}, 검증=${pipelineResult.nonLife.validated})",
        )

        return pipelineResult
    }

    /** 전체 파이프라인 실행 (초기 적재 모드) */
    suspend fun runFullPipeline(): PipelineResult = runWithOptions(PipelineOptions())

    /** 증분 수집 모드 (변경분만) */
    suspend fun runIncrementalPipeline(): PipelineResult = runWithOptions(PipelineOptions(incremental = true))

    /** 단일 보험 유형 파이프라인 */
    private suspend fun runForInsuranceType(
        insuranceType: InsuranceType,
        incremental: Boolean,
        categoryFilter: List<String> = emptyList(),
        activeSteps: Set<PipelineStep>,
        existingBatchId: String? = null,
    ): TypePipelineResult {
        val batchId = existingBatchId ?: UUID.randomUUID().toString()
        val errors = mutableListOf<String>()
        var crawledCount = 0
        var productsToProcess = emptyList<ProductRawData>()
        var validatedProducts = emptyList<ProductRawData>()
        var publishedCount = 0
        var embeddedCount = 0

        try {
            // CRAWL 단계
            if (PipelineStep.CRAWL in activeSteps) {
                pipelineService.startBatch(insuranceType, batchId)

                val crawlResult =
                    when (insuranceType) {
                        InsuranceType.LIFE -> lifeInsuranceCrawler.crawlAll(categoryFilter)
                        InsuranceType.NON_LIFE -> nonLifeInsuranceCrawler.crawlAll(categoryFilter)
                    }

                crawledCount = crawlResult.totalProducts
                if (crawlResult.products.isEmpty()) {
                    pipelineService.failBatch(batchId, "수집된 상품 0건")
                    return TypePipelineResult(0, 0, 0, 0, 0, listOf("수집된 상품 0건"), batchId)
                }

                // 변경 감지 (증분 모드)
                productsToProcess =
                    if (incremental) {
                        val changes = changeDetectionService.detectChanges(crawlResult.products, insuranceType)
                        if (changes.deletedProductIds.isNotEmpty()) {
                            stagingPublisher.deactivateProducts(changes.deletedProductIds)
                        }
                        changes.newProducts + changes.changedProducts
                    } else {
                        crawlResult.products
                    }

                if (productsToProcess.isEmpty()) {
                    pipelineService.completeBatch(batchId, crawledCount, crawledCount, 0)
                    return TypePipelineResult(crawledCount, 0, 0, 0, 0, emptyList(), batchId)
                }

                // 스테이징 저장
                for (product in productsToProcess) {
                    val entity = crawlStagingMapper.toStagingEntity(product, batchId)
                    crawlStagingRepository.save(entity)
                }

                logger.info("CRAWL 완료: batchId=$batchId, ${productsToProcess.size}건 스테이징 저장")
            }

            // VALIDATE 단계
            if (PipelineStep.VALIDATE in activeSteps) {
                // CRAWL 없이 VALIDATE만 실행 시 스테이징에서 데이터 복원
                if (PipelineStep.CRAWL !in activeSteps) {
                    val stagingRecords = crawlStagingRepository.findByBatchId(batchId).toList()
                    if (stagingRecords.isEmpty()) {
                        return TypePipelineResult(0, 0, 0, 0, 0, listOf("batchId=${batchId}에 해당하는 스테이징 데이터 없음"), batchId)
                    }
                    productsToProcess = stagingRecords.map { crawlStagingMapper.fromStagingEntity(it) }
                    crawledCount = productsToProcess.size
                    logger.info("스테이징에서 데이터 복원: batchId=$batchId, ${productsToProcess.size}건")
                }

                // L1~L4 검증
                val validationResult = validationService.validate(productsToProcess, batchId, insuranceType.name)
                validatedProducts = validationResult.passed

                // 스테이징 레코드 상태 업데이트
                val stagingRecords = crawlStagingRepository.findByBatchId(batchId).toList()
                val passedNames = validationResult.passed.map { it.productName }.toSet()
                for (record in stagingRecords) {
                    val rawData = crawlStagingMapper.fromStagingEntity(record)
                    val status = if (rawData.productName in passedNames) "PASSED" else "FAILED"
                    crawlStagingRepository.save(record.copy(validationStatus = status))
                }

                // 크로스 검증 (경고만, 파이프라인 중단 없음)
                for (product in validationResult.passed) {
                    try {
                        crossValidator.validate(
                            associationCoverages = product.coverages,
                            pdfArticles = emptyList(),
                            productName = product.productName,
                            companyName = product.companyName,
                        )
                    } catch (e: Exception) {
                        logger.warn("크로스 검증 오류: ${product.productName}: ${e.message}")
                    }
                }

                logger.info("VALIDATE 완료: batchId=$batchId, 통과=${validationResult.passed.size}, 실패=${validationResult.failed.size}")
            }

            // PUBLISH 단계
            if (PipelineStep.PUBLISH in activeSteps) {
                val publishResult = stagingPublisher.publish(batchId)
                publishedCount = publishResult.productsCreated + publishResult.productsUpdated
                logger.info("PUBLISH 완료: batchId=$batchId, 신규=${publishResult.productsCreated}, 수정=${publishResult.productsUpdated}")
            }

            // EMBED 단계
            if (PipelineStep.EMBED in activeSteps) {
                // 검증 통과 목록이 없으면 스테이징에서 PASSED 레코드 복원
                if (validatedProducts.isEmpty()) {
                    val passedRecords = crawlStagingRepository.findByBatchIdAndValidationStatus(batchId, "PASSED").toList()
                    validatedProducts = passedRecords.map { crawlStagingMapper.fromStagingEntity(it) }
                }

                val companyMap = buildCompanyMap()
                for (product in validatedProducts) {
                    try {
                        val companyId = companyMap[product.companyName] ?: continue
                        val matchedProduct =
                            productRepository
                                .findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                                .toList()
                                .find { it.name == product.productName }
                                ?: continue

                        val articles =
                            policyArticleRepository
                                .findByProductId(matchedProduct.id!!)
                                .toList()
                                .filter { it.isActive }

                        if (articles.isNotEmpty()) {
                            embeddedCount += embeddingService.embedArticles(articles)
                        }
                    } catch (e: Exception) {
                        errors.add("임베딩 실패: ${product.productName}: ${e.message}")
                        logger.error("임베딩 실패: ${product.productName}", e)
                    }
                }
                logger.info("EMBED 완료: batchId=$batchId, ${embeddedCount}건")
            }

            // 배치 완료 기록
            val validatedCount = validatedProducts.size
            pipelineService.completeBatch(
                batchId = batchId,
                productCount = crawledCount,
                successCount = validatedCount,
                failureCount = crawledCount - validatedCount,
            )

            return TypePipelineResult(
                crawled = crawledCount,
                parsed = productsToProcess.size,
                validated = validatedCount,
                published = publishedCount,
                embedded = embeddedCount,
                errors = errors,
                batchId = batchId,
            )
        } catch (e: Exception) {
            logger.error("파이프라인 전체 실패: insuranceType=$insuranceType", e)
            try {
                pipelineService.failBatch(batchId, e.message ?: "알 수 없는 오류")
            } catch (failEx: Exception) {
                logger.error("배치 실패 기록 오류", failEx)
            }
            return TypePipelineResult(0, 0, 0, 0, 0, listOf(e.message ?: "알 수 없는 오류"), batchId)
        }
    }

    private suspend fun buildCompanyMap(): Map<String, UUID> {
        val companies = insuranceCompanyRepository.findByIsActiveTrue().toList()
        return companies.associate { it.name to it.id!! }
    }
}
