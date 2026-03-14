package com.insuranceai.pipeline.usecase

import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.LifeSummaryPdfCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeSummaryPdfCrawler
import com.insuranceai.pipeline.domain.PipelineStep
import com.insuranceai.pipeline.domain.ValidationStatus
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.embedder.EmbeddingService
import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.parser.ParsingException
import com.insuranceai.pipeline.parser.PdfParsingService
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.pipeline.service.ChangeDetectionService
import com.insuranceai.pipeline.service.PipelineService
import com.insuranceai.pipeline.service.StagingPublisher
import com.insuranceai.pipeline.validator.CrossValidator
import com.insuranceai.pipeline.validator.ValidationService
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.repository.DataSourceRepository
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.ProductRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.measureTimedValue

data class PipelineOptions(
    val type: InsuranceType? = null,
    val categories: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    val incremental: Boolean = false,
    val dryRun: Boolean = false,
    val steps: Set<PipelineStep> = emptySet(),
    val batchId: String? = null,
    val force: Boolean = false,
) {
    /** 실행할 단계 결정: steps 명시 > dryRun > 전체 (SUMMARY_PDF는 명시적으로만 실행) */
    fun resolveSteps(): Set<PipelineStep> =
        when {
            steps.isNotEmpty() -> steps
            dryRun -> setOf(PipelineStep.CRAWL, PipelineStep.VALIDATE)
            else -> PipelineStep.entries.toSet() - setOf(PipelineStep.SUMMARY_PDF, PipelineStep.PARSE_PDF)
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
    private val lifeSummaryPdfCrawler: LifeSummaryPdfCrawler,
    private val nonLifeSummaryPdfCrawler: NonLifeSummaryPdfCrawler,
    private val changeDetectionService: ChangeDetectionService,
    private val validationService: ValidationService,
    private val crossValidator: CrossValidator,
    private val stagingPublisher: StagingPublisher,
    private val embeddingService: EmbeddingService,
    private val pipelineService: PipelineService,
    private val pdfParsingService: PdfParsingService,
    private val dataSourceRepository: DataSourceRepository,
    private val crawlStagingRepository: CrawlStagingRepository,
    private val crawlStagingMapper: CrawlStagingMapper,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
    private val policyArticleRepository: PolicyArticleRepository,
    private val productRepository: ProductRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 파싱 실패율 임계치 (20% 초과 시 배치 중단) */
        const val PARSING_FAILURE_THRESHOLD = 0.20

        /** 상품당 파싱 대상 숫자/날짜 필드 수 추정 */
        const val EXPECTED_FIELDS_PER_PRODUCT = 8
    }

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
                    runForInsuranceType(
                        type,
                        options.incremental,
                        options.categories,
                        options.companies,
                        activeSteps,
                        options.batchId,
                        options.force,
                    )
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
        companyFilter: List<String> = emptyList(),
        activeSteps: Set<PipelineStep>,
        existingBatchId: String? = null,
        force: Boolean = false,
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

                // 보험사 필터 적용
                val filteredProducts =
                    if (companyFilter.isNotEmpty()) {
                        val companySet = companyFilter.map { it.lowercase() }.toSet()
                        crawlResult.products.filter { it.companyName.lowercase() in companySet }
                    } else {
                        crawlResult.products
                    }
                if (companyFilter.isNotEmpty()) {
                    logger.info("보험사 필터 적용: $companyFilter, 크롤링 ${crawlResult.totalProducts}건 → 필터 후 ${filteredProducts.size}건")
                }

                crawledCount = filteredProducts.size
                if (filteredProducts.isEmpty()) {
                    pipelineService.failBatch(batchId, "수집된 상품 0건")
                    return TypePipelineResult(0, 0, 0, 0, 0, listOf("수집된 상품 0건"), batchId)
                }

                // 파싱 실패율 검사
                if (crawlResult.parsingFailureCount > 0) {
                    val totalFields = crawlResult.products.size * EXPECTED_FIELDS_PER_PRODUCT
                    val failureRate = crawlResult.parsingFailureCount.toDouble() / maxOf(totalFields, 1)
                    logger.info(
                        "배치 파싱 결과: batchId={}, 상품={}건, 파싱 경고={}건, 실패율={:.1f}%",
                        batchId,
                        crawlResult.products.size,
                        crawlResult.parsingFailureCount,
                        failureRate * 100,
                    )

                    if (failureRate > PARSING_FAILURE_THRESHOLD) {
                        throw ParsingException(
                            "파싱 실패율 ${String.format("%.1f", failureRate * 100)}%가 " +
                                "임계치 ${(PARSING_FAILURE_THRESHOLD * 100).toInt()}%를 초과했습니다. " +
                                "경고 ${crawlResult.parsingFailureCount}건",
                        )
                    }
                }

                // 변경 감지 (증분 모드)
                productsToProcess =
                    if (incremental) {
                        val changes = changeDetectionService.detectChanges(filteredProducts, insuranceType)
                        if (changes.deletedProductIds.isNotEmpty()) {
                            stagingPublisher.deactivateProducts(changes.deletedProductIds)
                        }
                        changes.newProducts + changes.changedProducts
                    } else {
                        filteredProducts
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
                    val status = if (rawData.productName in passedNames) ValidationStatus.PASSED.name else ValidationStatus.FAILED.name
                    crawlStagingRepository.save(record.copy(validationStatus = status))
                }

                logger.info("VALIDATE 완료: batchId=$batchId, 통과=${validationResult.passed.size}, 실패=${validationResult.failed.size}")
            }

            // PUBLISH 단계
            if (PipelineStep.PUBLISH in activeSteps) {
                val publishResult = stagingPublisher.publish(batchId)
                publishedCount = publishResult.productsCreated + publishResult.productsUpdated
                logger.info("PUBLISH 완료: batchId=$batchId, 신규=${publishResult.productsCreated}, 수정=${publishResult.productsUpdated}")
            }

            // SUMMARY_PDF 단계 (PDF 다운로드 + crawl_staging에 경로 반영)
            if (PipelineStep.SUMMARY_PDF in activeSteps) {
                try {
                    val pdfInfos =
                        when (insuranceType) {
                            InsuranceType.LIFE -> lifeSummaryPdfCrawler.collectAll(categoryFilter)
                            InsuranceType.NON_LIFE -> nonLifeSummaryPdfCrawler.collectAll(categoryFilter)
                        }

                    if (pdfInfos.isNotEmpty()) {
                        val report =
                            when (insuranceType) {
                                InsuranceType.LIFE -> lifeSummaryPdfCrawler.downloadAll(pdfInfos)
                                InsuranceType.NON_LIFE -> nonLifeSummaryPdfCrawler.downloadAll(pdfInfos)
                            }

                        // crawl_staging 레코드에 pdfSummaryPath 반영
                        val passedRecords =
                            crawlStagingRepository
                                .findByBatchIdAndValidationStatus(batchId, ValidationStatus.PASSED.name)
                                .toList()

                        for (record in passedRecords) {
                            val rawData = crawlStagingMapper.fromStagingEntity(record)
                            val key = "${rawData.companyName}::${rawData.productName}"
                            val pdfPath = report.downloadedFiles[key]

                            if (pdfPath != null && rawData.pdfSummaryPath == null) {
                                val updatedRawData = rawData.copy(pdfSummaryPath = pdfPath.toString())
                                crawlStagingRepository.save(
                                    record.copy(entityData = crawlStagingMapper.toJson(updatedRawData)),
                                )
                            }
                        }

                        logger.info(
                            "SUMMARY_PDF 완료: batchId=$batchId, " +
                                "다운로드=${report.downloaded}, 스킵=${report.skipped}, 실패=${report.failed}",
                        )
                    } else {
                        logger.info("SUMMARY_PDF: 다운로드할 PDF 정보 없음")
                    }
                } catch (e: Exception) {
                    logger.error("SUMMARY_PDF 단계 실패: ${e.message}", e)
                    errors.add("SUMMARY_PDF 실패: ${e.message}")
                }
            }

            // PARSE_PDF 단계 (PDF 파싱 → policy_articles + data_sources 저장)
            if (PipelineStep.PARSE_PDF in activeSteps) {
                val companyMap = buildCompanyMap()

                val passedRecords =
                    crawlStagingRepository
                        .findByBatchIdAndValidationStatus(batchId, ValidationStatus.PASSED.name)
                        .toList()

                val productsWithPdf =
                    passedRecords
                        .map { crawlStagingMapper.fromStagingEntity(it) }
                        .filter { it.pdfSummaryPath != null }

                for (product in productsWithPdf) {
                    try {
                        val file = File(product.pdfSummaryPath!!)
                        if (!file.exists()) {
                            logger.warn("PDF 파일 없음: ${product.pdfSummaryPath}")
                            continue
                        }

                        // productId 확보
                        val companyId = companyMap[product.companyName] ?: continue
                        val matchedProduct =
                            productRepository
                                .findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                                .toList()
                                .find { it.name == product.productName }
                                ?: continue
                        val productId = matchedProduct.id!!

                        // PDF 파싱
                        val parsingResult =
                            pdfParsingService.parse(file, productId, product.companyName, product.productName, forceExtract = force)

                        // DataSource 저장 (product_id 직접 연결)
                        val savedDataSource =
                            dataSourceRepository.save(
                                parsingResult.dataSource.copy(productId = productId),
                            )

                        // articles에 dataSourceId 연결 후 저장 (PDF 기반 contentType만 비활성화)
                        val articlesWithDataSource =
                            parsingResult.articles.map { it.copy(dataSourceId = savedDataSource.id) }
                        if (articlesWithDataSource.isNotEmpty()) {
                            // PDF에서 추출된 contentType들만 비활성화하고 저장
                            val pdfContentTypes = articlesWithDataSource.map { it.contentType }.toSet()
                            for (contentType in pdfContentTypes) {
                                val articlesOfType = articlesWithDataSource.filter { it.contentType == contentType }
                                stagingPublisher.publishArticlesWithVersioning(productId, articlesOfType, contentType)
                            }
                        }

                        // 크로스 검증 (실제 PDF 데이터 전달)
                        try {
                            crossValidator.validate(
                                associationCoverages = product.coverages,
                                pdfArticles = listOf(parsingResult.structuredData),
                                productName = product.productName,
                                companyName = product.companyName,
                            )
                        } catch (e: Exception) {
                            logger.warn("크로스 검증 오류: ${product.productName}: ${e.message}")
                        }

                        logger.debug(
                            "PARSE_PDF: ${product.companyName}/${product.productName} — " +
                                "조문 ${articlesWithDataSource.size}건, 신뢰도 ${parsingResult.confidence}",
                        )
                    } catch (e: Exception) {
                        errors.add("PDF 파싱 실패: ${product.productName}: ${e.message}")
                        logger.error("PDF 파싱 실패: ${product.companyName}/${product.productName}", e)
                    }
                }

                logger.info("PARSE_PDF 완료: batchId=$batchId, PDF 파싱 대상 ${productsWithPdf.size}건")
            }

            // EMBED 단계
            if (PipelineStep.EMBED in activeSteps) {
                // 검증 통과 목록이 없으면 스테이징에서 PASSED 레코드 복원
                if (validatedProducts.isEmpty()) {
                    val passedRecords =
                        crawlStagingRepository.findByBatchIdAndValidationStatus(batchId, ValidationStatus.PASSED.name).toList()
                    validatedProducts = passedRecords.map { crawlStagingMapper.fromStagingEntity(it) }
                }

                if (validatedProducts.isNotEmpty()) {
                    // batchId 기반 임베딩: 해당 배치의 상품만 처리
                    embeddedCount = embedByValidatedProducts(validatedProducts, errors)
                } else {
                    // batchId 없이 EMBED 단독 실행: 전체 active products 대상
                    logger.info("배치 기반 상품 없음 → 전체 active products 대상 임베딩 실행 (type=$insuranceType, categories=$categoryFilter)")
                    embeddedCount = embedAllActiveProducts(insuranceType, categoryFilter, errors)
                }
                logger.info("EMBED 완료: batchId=$batchId, ${embeddedCount}건")
            }

            // 배치 완료 기록 (기존 batchId가 있을 때만)
            val validatedCount = validatedProducts.size
            if (existingBatchId != null || PipelineStep.CRAWL in activeSteps) {
                pipelineService.completeBatch(
                    batchId = batchId,
                    productCount = crawledCount,
                    successCount = validatedCount,
                    failureCount = crawledCount - validatedCount,
                )
            }

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

    /** batchId 기반: 검증 통과 상품 목록에서 임베딩 */
    private suspend fun embedByValidatedProducts(
        validatedProducts: List<ProductRawData>,
        errors: MutableList<String>,
    ): Int {
        var count = 0
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
                    count += embeddingService.embedArticles(articles)
                }
            } catch (e: Exception) {
                errors.add("임베딩 실패: ${product.productName}: ${e.message}")
                logger.error("임베딩 실패: ${product.productName}", e)
            }
        }
        return count
    }

    /** 전체 active products 대상 임베딩 (batchId 없이 EMBED 단독 실행 시) */
    private suspend fun embedAllActiveProducts(
        insuranceType: InsuranceType,
        categoryFilter: List<String> = emptyList(),
        errors: MutableList<String>,
    ): Int {
        val typePrefix =
            when (insuranceType) {
                InsuranceType.LIFE -> "LIFE"
                InsuranceType.NON_LIFE -> "NON_LIFE"
            }
        // 보험 유형에 해당하는 보험사 ID 목록
        val companyIds =
            insuranceCompanyRepository
                .findByIsActiveTrue()
                .toList()
                .filter { it.type == typePrefix }
                .mapNotNull { it.id }
                .toSet()

        val allProducts =
            productRepository
                .findByIsActiveTrue()
                .toList()
                .filter { it.insuranceCompanyId in companyIds }
                .let { products ->
                    if (categoryFilter.isEmpty()) {
                        products
                    } else {
                        products.filter { it.category in categoryFilter }
                    }
                }

        logger.info("전체 임베딩 대상: ${allProducts.size}개 상품 (type=$insuranceType, categories=$categoryFilter)")

        var count = 0
        for (product in allProducts) {
            try {
                val articles =
                    policyArticleRepository
                        .findByProductId(product.id!!)
                        .toList()
                        .filter { it.isActive }

                if (articles.isNotEmpty()) {
                    count += embeddingService.embedArticles(articles)
                }
            } catch (e: Exception) {
                errors.add("임베딩 실패: ${product.name}: ${e.message}")
                logger.error("임베딩 실패: ${product.name}", e)
            }
        }
        return count
    }

    private suspend fun buildCompanyMap(): Map<String, UUID> {
        val companies = insuranceCompanyRepository.findByIsActiveTrue().toList()
        val map = mutableMapOf<String, UUID>()
        for (company in companies) {
            val companyId = company.id ?: continue
            map[company.name] = companyId
            company.aliases.forEach { alias -> map[alias] = companyId }
        }
        return map
    }
}
