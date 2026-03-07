package com.insuranceai.pipeline.usecase

import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
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

    @Test
    fun `크롤링 결과가 비어있으면 실패 처리`() =
        runTest {
            val emptyCrawlResult =
                CrawlResult(
                    insuranceType = InsuranceType.LIFE,
                    totalProducts = 0,
                    successCount = 0,
                    failureCount = 0,
                    products = emptyList(),
                    errors = emptyList(),
                )

            coEvery { pipelineService.startBatch(any(), any()) } returns
                CrawlHistoryEntity(batchId = "b1", insuranceType = "LIFE", totalProducts = 0, successCount = 0, failureCount = 0)
            coEvery { lifeInsuranceCrawler.crawlAll() } returns emptyCrawlResult
            coEvery { nonLifeInsuranceCrawler.crawlAll() } returns emptyCrawlResult
            coEvery { pipelineService.failBatch(any(), any()) } returns Unit

            val result = useCase.runFullPipeline()

            assertEquals(0, result.life.crawled)
            assertEquals(0, result.nonLife.crawled)
            assertTrue(result.life.errors.isNotEmpty())
        }

    @Test
    fun `전체 파이프라인 정상 흐름`() =
        runTest {
            val crawlResult =
                CrawlResult(
                    insuranceType = InsuranceType.LIFE,
                    totalProducts = 1,
                    successCount = 1,
                    failureCount = 0,
                    products = listOf(testProduct),
                    errors = emptyList(),
                )
            val emptyCrawlResult =
                CrawlResult(
                    insuranceType = InsuranceType.NON_LIFE,
                    totalProducts = 0,
                    successCount = 0,
                    failureCount = 0,
                    products = emptyList(),
                    errors = emptyList(),
                )

            coEvery { pipelineService.startBatch(any(), any()) } returns
                CrawlHistoryEntity(batchId = "b1", insuranceType = "LIFE", totalProducts = 0, successCount = 0, failureCount = 0)
            coEvery { lifeInsuranceCrawler.crawlAll() } returns crawlResult
            coEvery { nonLifeInsuranceCrawler.crawlAll() } returns emptyCrawlResult
            coEvery { pipelineService.failBatch(any(), any()) } returns Unit

            // 스테이징 저장
            every { crawlStagingMapper.toStagingEntity(any(), any()) } returns
                CrawlStagingEntity(batchId = "b1", entityType = "PRODUCT", entityData = "{}")
            coEvery { crawlStagingRepository.save(any()) } answers {
                firstArg<CrawlStagingEntity>().copy(id = UUID.randomUUID())
            }

            // 검증
            coEvery { validationService.validate(any(), any(), any()) } returns
                ValidationResult(
                    passed = listOf(testProduct),
                    failed = emptyList(),
                    warnings = emptyList(),
                    batchHeld = false,
                )

            // 스테이징 상태 업데이트
            coEvery { crawlStagingRepository.findByBatchId(any()) } returns
                flowOf(
                    CrawlStagingEntity(
                        id = UUID.randomUUID(),
                        batchId = "b1",
                        entityType = "PRODUCT",
                        entityData = "{}",
                    ),
                )
            every { crawlStagingMapper.fromStagingEntity(any()) } returns testProduct

            // 크로스 검증
            every { crossValidator.validate(any(), any(), any(), any()) } returns
                CrossValidator.CrossValidationResult(1, 0, 0, 0, 0, emptyList())

            // 반영
            coEvery { stagingPublisher.publish(any()) } returns
                PublishResult(1, 0, 0, 0, 0, 0, 0)

            // 임베딩
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
            val productId = UUID.randomUUID()
            coEvery {
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
            } returns
                flowOf(
                    ProductEntity(id = productId, insuranceCompanyId = companyId, name = "종신보험A", category = "종신보험"),
                )
            coEvery { policyArticleRepository.findByProductId(productId) } returns flowOf()

            coEvery { pipelineService.completeBatch(any(), any(), any(), any()) } returns Unit

            val result = useCase.runFullPipeline()

            assertEquals(1, result.life.crawled)
            assertEquals(1, result.life.validated)
            coVerify { stagingPublisher.publish(any()) }
        }

    @Test
    fun `개별 상품 임베딩 실패 시 전체 파이프라인은 계속 진행`() =
        runTest {
            val crawlResult =
                CrawlResult(
                    insuranceType = InsuranceType.LIFE,
                    totalProducts = 1,
                    successCount = 1,
                    failureCount = 0,
                    products = listOf(testProduct),
                    errors = emptyList(),
                )
            val emptyCrawlResult =
                CrawlResult(
                    insuranceType = InsuranceType.NON_LIFE,
                    totalProducts = 0,
                    successCount = 0,
                    failureCount = 0,
                    products = emptyList(),
                    errors = emptyList(),
                )

            coEvery { pipelineService.startBatch(any(), any()) } returns
                CrawlHistoryEntity(batchId = "b1", insuranceType = "LIFE", totalProducts = 0, successCount = 0, failureCount = 0)
            coEvery { lifeInsuranceCrawler.crawlAll() } returns crawlResult
            coEvery { nonLifeInsuranceCrawler.crawlAll() } returns emptyCrawlResult
            coEvery { pipelineService.failBatch(any(), any()) } returns Unit

            every { crawlStagingMapper.toStagingEntity(any(), any()) } returns
                CrawlStagingEntity(batchId = "b1", entityType = "PRODUCT", entityData = "{}")
            coEvery { crawlStagingRepository.save(any()) } answers {
                firstArg<CrawlStagingEntity>().copy(id = UUID.randomUUID())
            }
            coEvery { validationService.validate(any(), any(), any()) } returns
                ValidationResult(listOf(testProduct), emptyList(), emptyList(), false)
            coEvery { crawlStagingRepository.findByBatchId(any()) } returns
                flowOf(CrawlStagingEntity(id = UUID.randomUUID(), batchId = "b1", entityType = "PRODUCT", entityData = "{}"))
            every { crawlStagingMapper.fromStagingEntity(any()) } returns testProduct
            every { crossValidator.validate(any(), any(), any(), any()) } returns
                CrossValidator.CrossValidationResult(1, 0, 0, 0, 0, emptyList())
            coEvery { stagingPublisher.publish(any()) } returns PublishResult(1, 0, 0, 0, 0, 0, 0)

            // 임베딩 시 예외
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns flowOf(company)
            coEvery {
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId)
            } throws RuntimeException("DB 오류")

            coEvery { pipelineService.completeBatch(any(), any(), any(), any()) } returns Unit

            val result = useCase.runFullPipeline()

            // 임베딩 실패해도 파이프라인은 완료
            assertEquals(1, result.life.crawled)
            assertTrue(result.life.errors.isNotEmpty())
            coVerify { pipelineService.completeBatch(any(), any(), any(), any()) }
        }
}
