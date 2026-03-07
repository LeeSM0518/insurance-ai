package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class PublishResult(
    val productsCreated: Int,
    val productsUpdated: Int,
    val articlesCreated: Int,
    val premiumExamplesCreated: Int,
    val indicesCreated: Int,
    val metricsCreated: Int,
    val surrenderValuesCreated: Int,
)

@Service
class StagingPublisher(
    private val productRepository: ProductRepository,
    private val policyArticleRepository: PolicyArticleRepository,
    private val premiumExampleRepository: PremiumExampleRepository,
    private val productIndexRepository: ProductIndexRepository,
    private val productMetricRepository: ProductMetricRepository,
    private val surrenderValueRepository: SurrenderValueRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
    private val normalizationService: NormalizationService,
    private val crawlStagingRepository: CrawlStagingRepository,
    private val crawlStagingMapper: CrawlStagingMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 스테이징 -> 본 테이블 반영 (검증 통과 레코드만) */
    @Transactional
    suspend fun publish(batchId: String): PublishResult {
        val passedRecords =
            crawlStagingRepository
                .findByBatchIdAndValidationStatus(batchId, "PASSED")
                .toList()

        if (passedRecords.isEmpty()) {
            logger.warn("반영할 검증 통과 레코드가 없습니다: batchId=$batchId")
            return PublishResult(0, 0, 0, 0, 0, 0, 0)
        }

        var productsCreated = 0
        var productsUpdated = 0
        var articlesCreated = 0
        var premiumExamplesCreated = 0
        var indicesCreated = 0
        var metricsCreated = 0
        var surrenderValuesCreated = 0

        // 보험사명 → ID 매핑 캐시
        val companyMap = buildCompanyMap()

        for (record in passedRecords) {
            try {
                val rawData = crawlStagingMapper.fromStagingEntity(record)
                val companyId = companyMap[rawData.companyName] ?: continue

                // 기존 상품 조회 (보험사 + 상품명으로)
                val existingProducts =
                    productRepository
                        .findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                        .toList()
                val existingProduct = existingProducts.find { it.name == rawData.productName }

                val productId: UUID
                if (existingProduct != null) {
                    // 변경 상품: checksum 갱신
                    productRepository.save(
                        existingProduct.copy(
                            checksum = rawData.checksum,
                            updatedAt = Instant.now(),
                        ),
                    )
                    productId = existingProduct.id!!
                    productsUpdated++
                } else {
                    // 신규 상품: INSERT
                    val newProduct = normalizationService.toProductEntity(rawData, companyId)
                    val saved = productRepository.save(newProduct)
                    productId = saved.id!!
                    productsCreated++
                }

                // 보충 데이터: 기존 삭제 후 재삽입
                val premiumCount = republishPremiumExamples(productId, rawData, companyId)
                premiumExamplesCreated += premiumCount

                val indexCount = republishProductIndices(productId, rawData)
                indicesCreated += indexCount

                val metricCount = republishProductMetrics(productId, rawData)
                metricsCreated += metricCount
            } catch (e: Exception) {
                logger.error("스테이징 반영 실패: recordId=${record.id}, error=${e.message}", e)
            }
        }

        val result =
            PublishResult(
                productsCreated = productsCreated,
                productsUpdated = productsUpdated,
                articlesCreated = articlesCreated,
                premiumExamplesCreated = premiumExamplesCreated,
                indicesCreated = indicesCreated,
                metricsCreated = metricsCreated,
                surrenderValuesCreated = surrenderValuesCreated,
            )

        logger.info(
            "스테이징 반영 완료: batchId=$batchId, " +
                "상품(신규=${result.productsCreated}, 수정=${result.productsUpdated}), " +
                "보험료예시=${result.premiumExamplesCreated}, 지수=${result.indicesCreated}, " +
                "지표=${result.metricsCreated}",
        )

        return result
    }

    /** 삭제된 상품 soft delete */
    suspend fun deactivateProducts(productIds: List<UUID>) {
        for (productId in productIds) {
            val product = productRepository.findById(productId) ?: continue
            productRepository.save(product.copy(isActive = false, updatedAt = Instant.now()))
        }
        logger.info("상품 비활성화: ${productIds.size}건")
    }

    /** 약관 버전 관리: 기존 is_active=false 전환 후 새 레코드 삽입 */
    suspend fun publishArticlesWithVersioning(
        productId: UUID,
        newArticles: List<PolicyArticleEntity>,
    ) {
        // 기존 조문 비활성화
        val existingArticles = policyArticleRepository.findByProductId(productId).toList()
        for (article in existingArticles) {
            if (article.isActive) {
                policyArticleRepository.save(article.copy(isActive = false, updatedAt = Instant.now()))
            }
        }

        // 새 조문 삽입
        for (article in newArticles) {
            policyArticleRepository.save(article.copy(productId = productId))
        }

        logger.info("약관 버전 관리: productId=$productId, 기존 비활성화=${existingArticles.size}건, 신규=${newArticles.size}건")
    }

    /** 보험사명 → ID 매핑 구성 */
    private suspend fun buildCompanyMap(): Map<String, UUID> {
        val companies = insuranceCompanyRepository.findByIsActiveTrue().toList()
        return companies.associate { it.name to it.id!! }
    }

    /** 보험료 예시 재발행 */
    private suspend fun republishPremiumExamples(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
        companyId: UUID,
    ): Int {
        // 기존 삭제
        val existing = premiumExampleRepository.findByProductId(productId).toList()
        for (item in existing) {
            premiumExampleRepository.deleteById(item.id!!)
        }

        // 재삽입
        var count = 0
        for (premium in rawData.premiumExamples) {
            premiumExampleRepository.save(
                com.insuranceai.policy.entity.PremiumExampleEntity(
                    productId = productId,
                    gender = premium.gender,
                    age = premium.age,
                    insurancePeriod = premium.insurancePeriod,
                    paymentPeriod = premium.paymentPeriod,
                    insuranceAmount = premium.insuranceAmount,
                    premium = premium.premium,
                ),
            )
            count++
        }
        return count
    }

    /** 상품 지수 재발행 */
    private suspend fun republishProductIndices(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
    ): Int {
        val existing = productIndexRepository.findByProductId(productId).toList()
        for (item in existing) {
            productIndexRepository.deleteById(item.id!!)
        }

        var count = 0
        for (index in rawData.indices) {
            productIndexRepository.save(
                com.insuranceai.policy.entity.ProductIndexEntity(
                    productId = productId,
                    indexType = index.indexType,
                    gender = index.gender,
                    coverageName = index.coverageName,
                    indexValue = index.value,
                    baseDate = index.baseDate ?: java.time.LocalDate.now(),
                ),
            )
            count++
        }
        return count
    }

    /** 상품 지표 재발행 */
    private suspend fun republishProductMetrics(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
    ): Int {
        val existing = productMetricRepository.findByProductId(productId).toList()
        for (item in existing) {
            productMetricRepository.deleteById(item.id!!)
        }

        var count = 0
        for (metric in rawData.metrics) {
            productMetricRepository.save(
                com.insuranceai.policy.entity.ProductMetricEntity(
                    productId = productId,
                    metricType = metric.metricType,
                    metricValue = metric.value,
                    referenceDate = metric.referenceDate,
                ),
            )
            count++
        }
        return count
    }
}
