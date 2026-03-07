package com.insuranceai.pipeline.usecase

import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
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
)

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
        val typesToRun =
            when (options.type) {
                InsuranceType.LIFE -> listOf(InsuranceType.LIFE)
                InsuranceType.NON_LIFE -> listOf(InsuranceType.NON_LIFE)
                null -> listOf(InsuranceType.LIFE, InsuranceType.NON_LIFE)
            }

        val (results, duration) =
            measureTimedValue {
                typesToRun.associateWith { type ->
                    runForInsuranceType(type, options.incremental, options.categories, options.dryRun)
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
        dryRun: Boolean = false,
    ): TypePipelineResult {
        val batchId = UUID.randomUUID().toString()
        val errors = mutableListOf<String>()

        try {
            // 1. 배치 이력 시작
            pipelineService.startBatch(insuranceType, batchId)

            // 2. 크롤링
            val crawlResult =
                when (insuranceType) {
                    InsuranceType.LIFE -> lifeInsuranceCrawler.crawlAll(categoryFilter)
                    InsuranceType.NON_LIFE -> nonLifeInsuranceCrawler.crawlAll(categoryFilter)
                }

            val crawledCount = crawlResult.totalProducts
            if (crawlResult.products.isEmpty()) {
                pipelineService.failBatch(batchId, "수집된 상품 0건")
                return TypePipelineResult(0, 0, 0, 0, 0, listOf("수집된 상품 0건"))
            }

            // 3. 변경 감지 (증분 모드)
            val productsToProcess =
                if (incremental) {
                    val changes = changeDetectionService.detectChanges(crawlResult.products, insuranceType)
                    // 삭제된 상품 비활성화
                    if (changes.deletedProductIds.isNotEmpty()) {
                        stagingPublisher.deactivateProducts(changes.deletedProductIds)
                    }
                    changes.newProducts + changes.changedProducts
                } else {
                    crawlResult.products
                }

            if (productsToProcess.isEmpty()) {
                pipelineService.completeBatch(batchId, crawledCount, crawledCount, 0)
                return TypePipelineResult(crawledCount, 0, 0, 0, 0, emptyList())
            }

            // 4. 스테이징 저장
            for (product in productsToProcess) {
                val entity = crawlStagingMapper.toStagingEntity(product, batchId)
                crawlStagingRepository.save(entity)
            }

            // 5. L1~L4 검증
            val validationResult = validationService.validate(productsToProcess, batchId, insuranceType.name)
            val validatedCount = validationResult.passed.size

            // 6. 스테이징 레코드 상태 업데이트
            val stagingRecords = crawlStagingRepository.findByBatchId(batchId).toList()
            val passedNames = validationResult.passed.map { it.productName }.toSet()
            for (record in stagingRecords) {
                val rawData = crawlStagingMapper.fromStagingEntity(record)
                val status = if (rawData.productName in passedNames) "PASSED" else "FAILED"
                crawlStagingRepository.save(record.copy(validationStatus = status))
            }

            // 7. 크로스 검증 (경고만, 파이프라인 중단 없음)
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

            // 8. 본 테이블 반영 (WAP) — dryRun 시 스킵
            var publishedCount = 0
            var embeddedCount = 0

            if (dryRun) {
                logger.info("dry-run 모드: publish/embed 단계 스킵 (크로스 검증까지 완료)")
            } else {
                val publishResult = stagingPublisher.publish(batchId)
                publishedCount = publishResult.productsCreated + publishResult.productsUpdated

                // 9. 임베딩 (신규/변경 조문)
                val companyMap = buildCompanyMap()
                for (product in validationResult.passed) {
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
            }

            // 10. 배치 완료
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
            )
        } catch (e: Exception) {
            logger.error("파이프라인 전체 실패: insuranceType=$insuranceType", e)
            try {
                pipelineService.failBatch(batchId, e.message ?: "알 수 없는 오류")
            } catch (failEx: Exception) {
                logger.error("배치 실패 기록 오류", failEx)
            }
            return TypePipelineResult(0, 0, 0, 0, 0, listOf(e.message ?: "알 수 없는 오류"))
        }
    }

    private suspend fun buildCompanyMap(): Map<String, UUID> {
        val companies = insuranceCompanyRepository.findByIsActiveTrue().toList()
        return companies.associate { it.name to it.id!! }
    }
}
