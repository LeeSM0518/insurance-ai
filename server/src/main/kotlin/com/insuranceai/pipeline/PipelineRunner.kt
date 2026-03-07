package com.insuranceai.pipeline

import com.insuranceai.pipeline.crawler.LifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("pipeline")
class PipelineRunner(
    private val lifeInsuranceCrawler: LifeInsuranceCrawler,
    private val nonLifeInsuranceCrawler: NonLifeInsuranceCrawler,
    private val crawlStagingRepository: CrawlStagingRepository,
    private val crawlStagingMapper: CrawlStagingMapper,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runBlocking {
            logger.info("파이프라인 CLI 모드 시작")
            val batchId = UUID.randomUUID().toString()

            // 1. 생보협회 크롤링
            val lifeResult = lifeInsuranceCrawler.crawlAll()
            logger.info("생보 크롤링 완료: ${lifeResult.totalProducts}건")

            // 2. 손보협회 크롤링
            val nonLifeResult = nonLifeInsuranceCrawler.crawlAll()
            logger.info("손보 크롤링 완료: ${nonLifeResult.totalProducts}건")

            // 3. 수집 건수 sanity check
            val allProducts = lifeResult.products + nonLifeResult.products
            val allErrors = lifeResult.errors + nonLifeResult.errors

            if (allProducts.isEmpty()) {
                logger.error("수집된 상품이 0건 — 크롤링 전체 실패. 사이트 접근 및 셀렉터 확인 필요")
                return@runBlocking
            }

            // 4. 필수 필드 검증
            val invalidProducts =
                allProducts.filter {
                    it.companyName.isBlank() || it.productName.isBlank() || it.coverages.isEmpty()
                }
            if (invalidProducts.isNotEmpty()) {
                logger.warn(
                    "필수 필드 누락 상품 ${invalidProducts.size}건: ${invalidProducts.map { it.productName }}",
                )
            }

            // 5. 스테이징 저장
            val stagingEntities = allProducts.map { crawlStagingMapper.toStagingEntity(it, batchId) }
            var savedCount = 0L
            for (entity in stagingEntities) {
                crawlStagingRepository.save(entity)
                savedCount++
            }

            // 6. 수집 리포트
            logger.info(
                """
                ===== 크롤링 수집 리포트 =====
                배치 ID: $batchId
                생보: ${lifeResult.totalProducts}건 (성공: ${lifeResult.successCount}, 실패: ${lifeResult.failureCount})
                손보: ${nonLifeResult.totalProducts}건 (성공: ${nonLifeResult.successCount}, 실패: ${nonLifeResult.failureCount})
                스테이징 저장: ${savedCount}건
                필수 필드 누락: ${invalidProducts.size}건
                에러 목록: ${allErrors.map { "[${it.category}] ${it.errorType}: ${it.errorMessage}" }}
                =============================
                """.trimIndent(),
            )
        }
    }
}
