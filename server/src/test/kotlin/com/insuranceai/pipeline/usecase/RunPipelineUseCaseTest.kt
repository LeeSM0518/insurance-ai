package com.insuranceai.pipeline.usecase

import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.domain.PipelineStep
import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.CrawlResult
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.embedder.EmbeddingService
import com.insuranceai.pipeline.entity.CrawlHistoryEntity
import com.insuranceai.pipeline.entity.CrawlStagingEntity
import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.pipeline.service.ChangeDetectionService
import com.insuranceai.pipeline.service.PipelineService
import com.insuranceai.pipeline.service.PublishResult
import com.insuranceai.pipeline.service.StagingPublisher
import com.insuranceai.pipeline.validator.CrossValidator
import com.insuranceai.pipeline.validator.ValidationResult
import com.insuranceai.pipeline.validator.ValidationService
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.entity.ProductEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunPipelineUseCaseTest {
    private val lifeInsuranceCrawler = mockk<LifeInsuranceCrawler>()
    private val nonLifeInsuranceCrawler = mockk<NonLifeInsuranceCrawler>()
    private val changeDetectionService = mockk<ChangeDetectionService>()
    private val validationService = mockk<ValidationService>()
    private val crossValidator = mockk<CrossValidator>()
    private val stagingPublisher = mockk<StagingPublisher>()
    private val embeddingService = mockk<EmbeddingService>()
    private val pipelineService = mockk<PipelineService>()
    private val crawlStagingRepository = mockk<CrawlStagingRepository>()
    private val crawlStagingMapper = mockk<CrawlStagingMapper>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val policyArticleRepository = mockk<PolicyArticleRepository>()
    private val productRepository = mockk<ProductRepository>()

    private val useCase =
        RunPipelineUseCase(
            lifeInsuranceCrawler,
            nonLifeInsuranceCrawler,
            changeDetectionService,
            validationService,
            crossValidator,
            stagingPublisher,
            embeddingService,
            pipelineService,
            crawlStagingRepository,
            crawlStagingMapper,
            insuranceCompanyRepository,
            policyArticleRepository,
            productRepository,
        )

    private val companyId = UUID.randomUUID()
    private val company = InsuranceCompanyEntity(id = companyId, name = "삼성생명", type = "LIFE")

    private val testProduct =
        ProductRawData(
            companyName = "삼성생명",
            productName = "종신보험A",
            category = "종신보험",
            coverages = listOf(CoverageData("사망", "사망시", "1억", "MAIN")),
            checksum = "hash123",
        )

    private fun setupEmptyCrawlResult(type: InsuranceType) =
        CrawlResult(
            insuranceType = type,
            totalProducts = 0,
            successCount = 0,
            failureCount = 0,
            products = emptyList(),
            errors = emptyList(),
        )

    private fun setupBatchMocks() {
        coEvery { pipelineService.startBatch(any(), any()) } returns
            CrawlHistoryEntity(batchId = "b1", insuranceType = "LIFE", totalProducts = 0, successCount = 0, failureCount = 0)
        coEvery { pipelineService.failBatch(any(), any()) } returns Unit
        coEvery { pipelineService.completeBatch(any(), any(), any(), any()) } returns Unit
    }

    private fun setupStagingMocks() {
        every { crawlStagingMapper.toStagingEntity(any(), any()) } returns
            CrawlStagingEntity(batchId = "b1", entityType = "PRODUCT", entityData = Json.of("{}"))
        coEvery { crawlStagingRepository.save(any()) } answers {
            firstArg<CrawlStagingEntity>().copy(id = UUID.randomUUID())
        }
    }

    private fun setupValidationMocks(passed: List<ProductRawData> = listOf(testProduct)) {
        coEvery { validationService.validate(any(), any(), any()) } returns
            ValidationResult(passed, emptyList(), emptyList(), false)
        coEvery { crawlStagingRepository.findByBatchId(any()) } returns
            flowOf(CrawlStagingEntity(id = UUID.randomUUID(), batchId = "b1", entityType = "PRODUCT", entityData = Json.of("{}")))
        every { crawlStagingMapper.fromStagingEntity(any()) } returns testProduct
        every { crossValidator.validate(any(), any(), any(), any()) } returns
            CrossValidator.CrossValidationResult(1, 0, 0, 0, 0, emptyList())
    }

    @Test
    fun `크롤링 결과가 비어있으면 실패 처리`() =
        runTest {
            setupBatchMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.LIFE)
            coEvery { nonLifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.NON_LIFE)

            val result = useCase.runFullPipeline()

            assertEquals(0, result.life.crawled)
            assertEquals(0, result.nonLife.crawled)
            assertTrue(result.life.errors.isNotEmpty())
        }

    @Test
    fun `전체 파이프라인 정상 흐름`() =
        runTest {
            val crawlResult =
                CrawlResult(InsuranceType.LIFE, 1, 1, 0, listOf(testProduct), emptyList())

            setupBatchMocks()
            setupStagingMocks()
            setupValidationMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns crawlResult
            coEvery { nonLifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.NON_LIFE)
            coEvery { stagingPublisher.publish(any()) } returns PublishResult(1, 0, 0, 0, 0, 0, 0)
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
            val productId = UUID.randomUUID()
            coEvery {
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
            } returns flowOf(ProductEntity(id = productId, insuranceCompanyId = companyId, name = "종신보험A", category = "종신보험"))
            coEvery { policyArticleRepository.findByProductId(productId) } returns flowOf()

            val result = useCase.runFullPipeline()

            assertEquals(1, result.life.crawled)
            assertEquals(1, result.life.validated)
            coVerify { stagingPublisher.publish(any()) }
        }

    @Test
    fun `개별 상품 임베딩 실패 시 전체 파이프라인은 계속 진행`() =
        runTest {
            val crawlResult =
                CrawlResult(InsuranceType.LIFE, 1, 1, 0, listOf(testProduct), emptyList())

            setupBatchMocks()
            setupStagingMocks()
            setupValidationMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns crawlResult
            coEvery { nonLifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.NON_LIFE)
            coEvery { stagingPublisher.publish(any()) } returns PublishResult(1, 0, 0, 0, 0, 0, 0)
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
            coEvery {
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
            } throws RuntimeException("DB 오류")

            val result = useCase.runFullPipeline()

            assertEquals(1, result.life.crawled)
            assertTrue(result.life.errors.isNotEmpty())
            coVerify { pipelineService.completeBatch(any(), any(), any(), any()) }
        }

    @Test
    fun `runWithOptions type=LIFE면 생보 크롤러만 호출`() =
        runTest {
            setupBatchMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.LIFE)

            val result = useCase.runWithOptions(PipelineOptions(type = InsuranceType.LIFE))

            coVerify(exactly = 1) { lifeInsuranceCrawler.crawlAll(any()) }
            coVerify(exactly = 0) { nonLifeInsuranceCrawler.crawlAll(any()) }
            assertEquals(0, result.nonLife.crawled)
        }

    @Test
    fun `runWithOptions type=NON_LIFE면 손보 크롤러만 호출`() =
        runTest {
            setupBatchMocks()
            coEvery { nonLifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.NON_LIFE)

            val result = useCase.runWithOptions(PipelineOptions(type = InsuranceType.NON_LIFE))

            coVerify(exactly = 0) { lifeInsuranceCrawler.crawlAll(any()) }
            coVerify(exactly = 1) { nonLifeInsuranceCrawler.crawlAll(any()) }
            assertEquals(0, result.life.crawled)
        }

    @Test
    fun `runWithOptions type=null이면 양쪽 크롤러 모두 호출`() =
        runTest {
            setupBatchMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.LIFE)
            coEvery { nonLifeInsuranceCrawler.crawlAll(any()) } returns setupEmptyCrawlResult(InsuranceType.NON_LIFE)

            useCase.runWithOptions(PipelineOptions(type = null))

            coVerify(exactly = 1) { lifeInsuranceCrawler.crawlAll(any()) }
            coVerify(exactly = 1) { nonLifeInsuranceCrawler.crawlAll(any()) }
        }

    @Test
    fun `runWithOptions categories가 크롤러에 전달됨`() =
        runTest {
            val categorySlot = slot<List<String>>()

            setupBatchMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(capture(categorySlot)) } returns setupEmptyCrawlResult(InsuranceType.LIFE)

            useCase.runWithOptions(PipelineOptions(type = InsuranceType.LIFE, categories = listOf("암보험")))

            assertEquals(listOf("암보험"), categorySlot.captured)
        }

    @Test
    fun `runWithOptions dryRun=true면 publish와 embed가 호출되지 않음`() =
        runTest {
            val crawlResult =
                CrawlResult(InsuranceType.LIFE, 1, 1, 0, listOf(testProduct), emptyList())

            setupBatchMocks()
            setupStagingMocks()
            setupValidationMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns crawlResult

            val result = useCase.runWithOptions(PipelineOptions(type = InsuranceType.LIFE, dryRun = true))

            coVerify(exactly = 0) { stagingPublisher.publish(any()) }
            coVerify(exactly = 0) { embeddingService.embedArticles(any()) }
            coVerify(exactly = 1) { crossValidator.validate(any(), any(), any(), any()) }
            assertEquals(1, result.life.crawled)
            assertEquals(0, result.life.published)
            assertEquals(0, result.life.embedded)
        }

    // --- steps 관련 테스트 ---

    @Test
    fun `steps=CRAWL이면 검증, 반영, 임베딩이 호출되지 않음`() =
        runTest {
            val crawlResult =
                CrawlResult(InsuranceType.LIFE, 1, 1, 0, listOf(testProduct), emptyList())

            setupBatchMocks()
            setupStagingMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns crawlResult

            val result =
                useCase.runWithOptions(
                    PipelineOptions(type = InsuranceType.LIFE, steps = setOf(PipelineStep.CRAWL)),
                )

            // 크롤링은 수행
            coVerify(exactly = 1) { lifeInsuranceCrawler.crawlAll(any()) }
            // 검증, 반영, 임베딩은 미수행
            coVerify(exactly = 0) { validationService.validate(any(), any(), any()) }
            coVerify(exactly = 0) { stagingPublisher.publish(any()) }
            coVerify(exactly = 0) { embeddingService.embedArticles(any()) }
            assertEquals(1, result.life.crawled)
            assertEquals(0, result.life.validated)
            // batchId가 반환됨
            assertTrue(result.life.batchId != null)
        }

    @Test
    fun `steps=VALIDATE이면 스테이징에서 데이터를 복원하여 검증`() =
        runTest {
            val batchId = "existing-batch-123"

            setupBatchMocks()
            setupStagingMocks()
            setupValidationMocks()

            val result =
                useCase.runWithOptions(
                    PipelineOptions(
                        type = InsuranceType.LIFE,
                        steps = setOf(PipelineStep.VALIDATE),
                        batchId = batchId,
                    ),
                )

            // 크롤링 미수행
            coVerify(exactly = 0) { lifeInsuranceCrawler.crawlAll(any()) }
            // 스테이징에서 데이터 로드 (복원 + 상태 업데이트로 2회 호출)
            coVerify(atLeast = 1) { crawlStagingRepository.findByBatchId(batchId) }
            // 검증 수행
            coVerify(exactly = 1) { validationService.validate(any(), any(), any()) }
            // 반영, 임베딩 미수행
            coVerify(exactly = 0) { stagingPublisher.publish(any()) }
            assertEquals(1, result.life.validated)
        }

    @Test
    fun `steps=PUBLISH이면 반영만 수행`() =
        runTest {
            val batchId = "existing-batch-123"

            setupBatchMocks()
            coEvery { stagingPublisher.publish(batchId) } returns PublishResult(2, 1, 0, 0, 0, 0, 0)

            val result =
                useCase.runWithOptions(
                    PipelineOptions(
                        type = InsuranceType.LIFE,
                        steps = setOf(PipelineStep.PUBLISH),
                        batchId = batchId,
                    ),
                )

            // 크롤링, 검증 미수행
            coVerify(exactly = 0) { lifeInsuranceCrawler.crawlAll(any()) }
            coVerify(exactly = 0) { validationService.validate(any(), any(), any()) }
            // 반영만 수행
            coVerify(exactly = 1) { stagingPublisher.publish(batchId) }
            assertEquals(3, result.life.published)
        }

    @Test
    fun `steps=EMBED이면 임베딩만 수행`() =
        runTest {
            val batchId = "existing-batch-123"
            val productId = UUID.randomUUID()

            setupBatchMocks()
            coEvery { crawlStagingRepository.findByBatchIdAndValidationStatus(batchId, "PASSED") } returns
                flowOf(CrawlStagingEntity(id = UUID.randomUUID(), batchId = batchId, entityType = "PRODUCT", entityData = Json.of("{}")))
            every { crawlStagingMapper.fromStagingEntity(any()) } returns testProduct
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
            coEvery {
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
            } returns flowOf(ProductEntity(id = productId, insuranceCompanyId = companyId, name = "종신보험A", category = "종신보험"))
            coEvery { policyArticleRepository.findByProductId(productId) } returns flowOf()

            val result =
                useCase.runWithOptions(
                    PipelineOptions(
                        type = InsuranceType.LIFE,
                        steps = setOf(PipelineStep.EMBED),
                        batchId = batchId,
                    ),
                )

            // 크롤링, 검증, 반영 미수행
            coVerify(exactly = 0) { lifeInsuranceCrawler.crawlAll(any()) }
            coVerify(exactly = 0) { validationService.validate(any(), any(), any()) }
            coVerify(exactly = 0) { stagingPublisher.publish(any()) }
            // PASSED 레코드 조회
            coVerify(exactly = 1) { crawlStagingRepository.findByBatchIdAndValidationStatus(batchId, "PASSED") }
        }

    @Test
    fun `steps 미지정이면 전체 실행 (하위 호환)`() =
        runTest {
            val crawlResult =
                CrawlResult(InsuranceType.LIFE, 1, 1, 0, listOf(testProduct), emptyList())

            setupBatchMocks()
            setupStagingMocks()
            setupValidationMocks()
            coEvery { lifeInsuranceCrawler.crawlAll(any()) } returns crawlResult
            coEvery { stagingPublisher.publish(any()) } returns PublishResult(1, 0, 0, 0, 0, 0, 0)
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
            val productId = UUID.randomUUID()
            coEvery {
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
            } returns flowOf(ProductEntity(id = productId, insuranceCompanyId = companyId, name = "종신보험A", category = "종신보험"))
            coEvery { policyArticleRepository.findByProductId(productId) } returns flowOf()

            val result = useCase.runWithOptions(PipelineOptions(type = InsuranceType.LIFE))

            // 전체 단계 수행
            coVerify(exactly = 1) { lifeInsuranceCrawler.crawlAll(any()) }
            coVerify(exactly = 1) { validationService.validate(any(), any(), any()) }
            coVerify(exactly = 1) { stagingPublisher.publish(any()) }
            assertEquals(1, result.life.crawled)
        }

    @Test
    fun `resolveSteps - steps 명시 시 dryRun보다 우선`() {
        val options =
            PipelineOptions(
                dryRun = true,
                steps = setOf(PipelineStep.CRAWL),
            )
        assertEquals(setOf(PipelineStep.CRAWL), options.resolveSteps())
    }

    @Test
    fun `resolveSteps - steps 미지정 + dryRun=true면 CRAWL+VALIDATE`() {
        val options = PipelineOptions(dryRun = true)
        assertEquals(setOf(PipelineStep.CRAWL, PipelineStep.VALIDATE), options.resolveSteps())
    }

    @Test
    fun `resolveSteps - 둘 다 미지정이면 SUMMARY_PDF 제외 전체 단계`() {
        val options = PipelineOptions()
        assertEquals(PipelineStep.entries.toSet() - PipelineStep.SUMMARY_PDF, options.resolveSteps())
    }
}
