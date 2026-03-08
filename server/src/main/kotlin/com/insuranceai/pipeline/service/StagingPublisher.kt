package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.mapper.CrawlStagingMapper
import com.insuranceai.pipeline.repository.CrawlStagingRepository
import com.insuranceai.policy.domain.ContentType
import com.insuranceai.policy.domain.SectionType
import com.insuranceai.policy.entity.DataSourceEntity
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.ProductFeatureEntity
import com.insuranceai.policy.entity.ProductYieldScenarioEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import com.insuranceai.policy.repository.DataSourceRepository
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductFeatureRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.ProductYieldScenarioRepository
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
    private val productFeatureRepository: ProductFeatureRepository,
    private val productYieldScenarioRepository: ProductYieldScenarioRepository,
    private val dataSourceRepository: DataSourceRepository,
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

        // 비교공시 배치의 DataSource 생성
        val dataSource =
            dataSourceRepository.save(
                DataSourceEntity(
                    sourceType = "ASSOCIATION",
                    sourceUrl =
                        passedRecords.firstOrNull()?.let {
                            crawlStagingMapper.fromStagingEntity(it).sourceUrl
                        },
                    crawledAt = Instant.now(),
                ),
            )

        // 보험사 전화번호 업데이트 추적 (보험사별 1회만)
        val updatedCompanyPhones = mutableSetOf<UUID>()

        for (record in passedRecords) {
            try {
                val rawData = crawlStagingMapper.fromStagingEntity(record)
                val companyId = companyMap[rawData.companyName] ?: continue

                // 보험사 전화번호 업데이트 (null → 크롤링 값)
                if (rawData.phone != null && companyId !in updatedCompanyPhones) {
                    val company = insuranceCompanyRepository.findById(companyId)
                    if (company != null && company.phone == null) {
                        insuranceCompanyRepository.save(company.copy(phone = rawData.phone))
                        updatedCompanyPhones.add(companyId)
                    }
                }

                // 기존 상품 조회 (보험사 + 상품명으로)
                val existingProducts =
                    productRepository
                        .findByInsuranceCompanyIdAndIsActiveTrue(companyId)
                        .toList()
                val existingProduct = existingProducts.find { it.name == rawData.productName }

                val productId: UUID
                if (existingProduct != null) {
                    // 변경 상품: 모든 크롤링 필드 갱신
                    val updated = normalizationService.toProductEntity(rawData, companyId)
                    productRepository.save(
                        existingProduct.copy(
                            renewalType = updated.renewalType,
                            salesChannel = updated.salesChannel,
                            minPremium = updated.minPremium,
                            appliedInterestRate = updated.appliedInterestRate,
                            declaredInterestRate = updated.declaredInterestRate,
                            guaranteedInterestRate = updated.guaranteedInterestRate,
                            guaranteedInterestRateText = updated.guaranteedInterestRateText,
                            expenseRatio = updated.expenseRatio,
                            surrenderType = updated.surrenderType,
                            productType = updated.productType,
                            salesStartDate = updated.salesStartDate,
                            isUniversal = updated.isUniversal,
                            notes = updated.notes,
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

                // 비교공시 coverages → policy_articles (COVERAGE) 저장 + DataSource 연결
                val coverageCount = republishCoverageArticles(productId, rawData, dataSource.id)
                articlesCreated += coverageCount

                // 비교공시 surrenderValues → surrender_values 저장
                val surrenderCount = republishSurrenderValues(productId, rawData)
                surrenderValuesCreated += surrenderCount

                // 보충 데이터: features, yieldScenarios
                republishProductFeatures(productId, rawData)
                republishProductYieldScenarios(productId, rawData)
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

    /**
     * 약관 버전 관리: 기존 is_active=false 전환 후 새 레코드 삽입
     * @param contentTypeFilter null이면 전체 비활성화, 지정 시 해당 contentType만 비활성화
     */
    suspend fun publishArticlesWithVersioning(
        productId: UUID,
        newArticles: List<PolicyArticleEntity>,
        contentTypeFilter: String? = null,
    ) {
        // 기존 조문 비활성화
        val existingArticles = policyArticleRepository.findByProductId(productId).toList()
        var deactivatedCount = 0
        for (article in existingArticles) {
            if (article.isActive && (contentTypeFilter == null || article.contentType == contentTypeFilter)) {
                policyArticleRepository.save(article.copy(isActive = false, updatedAt = Instant.now()))
                deactivatedCount++
            }
        }

        // 새 조문 삽입
        for (article in newArticles) {
            policyArticleRepository.save(article.copy(productId = productId))
        }

        logger.info(
            "약관 버전 관리: productId=$productId, 기존 비활성화=${deactivatedCount}건, 신규=${newArticles.size}건" +
                (contentTypeFilter?.let { " (contentType=$it)" } ?: ""),
        )
    }

    /** 보험사명 → ID 매핑 구성 (aliases 포함) */
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
                    insuranceAmountText = premium.insuranceAmountText,
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

    /** 비교공시 coverages → policy_articles (COVERAGE) 저장 + DataSource 연결 */
    private suspend fun republishCoverageArticles(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
        dataSourceId: UUID? = null,
    ): Int {
        if (rawData.coverages.isEmpty()) return 0

        val newArticles =
            rawData.coverages.map { coverage ->
                val sectionType =
                    when (coverage.sectionType) {
                        "MAIN" -> SectionType.MAIN.name
                        "RIDER" -> SectionType.RIDER.name
                        "MAIN_CONTRACT" -> SectionType.MAIN.name
                        else -> SectionType.MAIN.name
                    }

                PolicyArticleEntity(
                    productId = productId,
                    dataSourceId = dataSourceId,
                    content = "[${coverage.coverageName}] 지급사유: ${coverage.paymentReason}, 지급액: ${coverage.paymentAmount}",
                    contentType = ContentType.COVERAGE.name,
                    sectionType = sectionType,
                )
            }

        // COVERAGE 타입만 비활성화 후 재삽입
        publishArticlesWithVersioning(productId, newArticles, ContentType.COVERAGE.name)
        return newArticles.size
    }

    /** 비교공시 surrenderValues → surrender_values 저장 */
    private suspend fun republishSurrenderValues(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
    ): Int {
        if (rawData.surrenderValues.isEmpty()) return 0

        // 기존 삭제 후 재삽입
        surrenderValueRepository.deleteByProductId(productId)

        var count = 0
        for (sv in rawData.surrenderValues) {
            surrenderValueRepository.save(
                SurrenderValueEntity(
                    productId = productId,
                    elapsedMonths = sv.elapsedMonths,
                    gender = sv.gender,
                    premiumPaid = sv.premiumPaid,
                    surrenderValue = sv.surrenderValue,
                    surrenderRate = sv.surrenderRate,
                    policyholderReserve = sv.policyholderReserve,
                ),
            )
            count++
        }
        return count
    }

    /** 상품 특성(features) 재발행 */
    private suspend fun republishProductFeatures(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
    ) {
        if (rawData.features.isEmpty()) return

        productFeatureRepository.deleteByProductId(productId)

        for (feature in rawData.features) {
            productFeatureRepository.save(
                ProductFeatureEntity(
                    productId = productId,
                    key = feature.featureKey,
                    value = feature.featureValue,
                ),
            )
        }
    }

    /** 수익률 시나리오 재발행 */
    private suspend fun republishProductYieldScenarios(
        productId: UUID,
        rawData: com.insuranceai.pipeline.dto.ProductRawData,
    ) {
        if (rawData.yieldScenarios.isEmpty()) return

        productYieldScenarioRepository.deleteByProductId(productId)

        for (scenario in rawData.yieldScenarios) {
            productYieldScenarioRepository.save(
                ProductYieldScenarioEntity(
                    productId = productId,
                    scenarioType = scenario.scenarioType,
                    scenarioLabel = scenario.scenarioLabel,
                    yieldRate = scenario.yieldRate,
                    gender = scenario.gender,
                ),
            )
        }
    }
}
