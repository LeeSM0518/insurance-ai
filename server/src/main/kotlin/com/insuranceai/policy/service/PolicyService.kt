package com.insuranceai.policy.service

import com.insuranceai.chat.dto.SupplementaryData
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.policy.dto.PolicyArticleResponse
import com.insuranceai.policy.dto.PremiumExampleItem
import com.insuranceai.policy.dto.PremiumExampleResponse
import com.insuranceai.policy.dto.ProductFeatureItem
import com.insuranceai.policy.dto.ProductIndexItem
import com.insuranceai.policy.dto.ProductIndexResponse
import com.insuranceai.policy.dto.ProductMetricItem
import com.insuranceai.policy.dto.RelatedArticleResponse
import com.insuranceai.policy.dto.SurrenderValueItem
import com.insuranceai.policy.dto.SurrenderValueResponse
import com.insuranceai.policy.dto.YieldScenarioItem
import com.insuranceai.policy.error.PolicyError
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
import com.insuranceai.search.dto.CompanyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.springframework.core.io.PathResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class PolicyService(
    private val policyArticleRepository: PolicyArticleRepository,
    private val productRepository: ProductRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
    private val dataSourceRepository: DataSourceRepository,
    private val surrenderValueRepository: SurrenderValueRepository,
    private val premiumExampleRepository: PremiumExampleRepository,
    private val productIndexRepository: ProductIndexRepository,
    private val productFeatureRepository: ProductFeatureRepository,
    private val productYieldScenarioRepository: ProductYieldScenarioRepository,
    private val productMetricRepository: ProductMetricRepository,
) {
    /** 약관 조문 상세 조회 */
    suspend fun getArticleDetail(articleId: UUID): PolicyArticleResponse {
        val article =
            policyArticleRepository.findById(articleId)
                ?: throw PolicyError.ArticleNotFound

        val product =
            productRepository.findById(article.productId)
                ?: throw PolicyError.ArticleNotFound

        val company =
            insuranceCompanyRepository.findById(product.insuranceCompanyId)
                ?: throw PolicyError.ArticleNotFound

        val dataSource = article.dataSourceId?.let { dataSourceRepository.findById(it) }

        val relatedArticles = getRelatedArticles(articleId, article.productId)

        val hasPdfSource = dataSource?.filePath != null

        // 직접 PDF가 없으면 항상 상품 기반으로 PRODUCT_SUMMARY PDF 검색
        val linkedPdfDataSourceId =
            if (!hasPdfSource) {
                dataSourceRepository.findProductSummaryByProductId(article.productId)?.id
            } else {
                null
            }

        return PolicyArticleResponse(
            id = article.id!!,
            insuranceCompany =
                CompanyInfo(
                    id = company.id!!,
                    name = company.name,
                    type = company.type,
                ),
            productId = product.id!!,
            productName = product.name,
            articleNumber = article.articleNumber,
            articleTitle = article.articleTitle,
            content = article.content,
            contentType = article.contentType,
            sectionType = article.sectionType,
            effectiveDate = article.effectiveDate,
            dataSource = dataSource?.sourceType,
            dataSourceId = dataSource?.id,
            hasPdfSource = hasPdfSource || linkedPdfDataSourceId != null,
            linkedPdfDataSourceId = linkedPdfDataSourceId,
            relatedArticles = relatedArticles,
        )
    }

    /** PDF 파일 리소스 조회 */
    suspend fun getPdfResource(dataSourceId: UUID): Resource {
        val dataSource =
            dataSourceRepository.findById(dataSourceId)
                ?: throw PolicyError.DataSourceNotFound

        val filePath =
            dataSource.filePath
                ?: throw PolicyError.PdfFileNotFound

        // Path Traversal 방지: 허용 디렉토리 내인지 검증
        val allowedBase = Path.of("data").toAbsolutePath().normalize()
        val resolvedPath = Path.of(filePath).toAbsolutePath().normalize()
        if (!resolvedPath.startsWith(allowedBase)) {
            throw PolicyError.InvalidFilePath
        }

        // non-blocking 파일 존재 확인
        val exists =
            withContext(Dispatchers.IO) {
                Files.exists(resolvedPath)
            }
        if (!exists) {
            throw PolicyError.PdfFileNotFound
        }

        return PathResource(resolvedPath)
    }

    /** 관련 조문 조회 (같은 상품의 다른 조문, 최대 10개) */
    private suspend fun getRelatedArticles(
        currentArticleId: UUID,
        productId: UUID,
    ): List<RelatedArticleResponse> =
        policyArticleRepository
            .findByProductId(productId)
            .toList()
            .filter { it.id != currentArticleId }
            .take(10)
            .map {
                RelatedArticleResponse(
                    id = it.id!!,
                    articleNumber = it.articleNumber,
                    articleTitle = it.articleTitle,
                    contentType = it.contentType,
                )
            }

    /** 해약환급금 조회 */
    suspend fun getSurrenderValues(productId: UUID): SurrenderValueResponse =
        SurrenderValueResponse(
            productId = productId,
            values =
                surrenderValueRepository
                    .findByProductId(productId)
                    .toList()
                    .map {
                        SurrenderValueItem(
                            elapsedMonths = it.elapsedMonths,
                            premiumPaid = it.premiumPaid,
                            surrenderValue = it.surrenderValue,
                            surrenderRate = it.surrenderRate,
                        )
                    },
        )

    /** 보험료 예시 조회 */
    suspend fun getPremiumExamples(productId: UUID): PremiumExampleResponse =
        PremiumExampleResponse(
            productId = productId,
            examples =
                premiumExampleRepository
                    .findByProductId(productId)
                    .toList()
                    .map {
                        PremiumExampleItem(
                            gender = it.gender,
                            age = it.age,
                            insurancePeriod = it.insurancePeriod,
                            paymentPeriod = it.paymentPeriod,
                            insuranceAmount = it.insuranceAmount,
                            insuranceAmountText = it.insuranceAmountText,
                            premium = it.premium,
                        )
                    },
        )

    /** 가격지수 조회 */
    suspend fun getProductIndices(productId: UUID): ProductIndexResponse =
        ProductIndexResponse(
            productId = productId,
            indices =
                productIndexRepository
                    .findByProductId(productId)
                    .toList()
                    .map {
                        ProductIndexItem(
                            indexType = it.indexType,
                            gender = it.gender,
                            coverageName = it.coverageName,
                            indexValue = it.indexValue,
                            baseDate = it.baseDate,
                        )
                    },
        )

    /** REST API용 보충 데이터 일괄 조회 */
    suspend fun getSupplementaryData(
        productIds: List<UUID>,
        needs: SupplementaryNeeds,
    ): SupplementaryData =
        coroutineScope {
            if (productIds.isEmpty()) return@coroutineScope SupplementaryData()

            val surrenderDeferred =
                async {
                    if (needs.surrenderValue) {
                        surrenderValueRepository.findByProductIdIn(productIds).toList().map {
                            SurrenderValueItem(
                                elapsedMonths = it.elapsedMonths,
                                premiumPaid = it.premiumPaid,
                                surrenderValue = it.surrenderValue,
                                surrenderRate = it.surrenderRate,
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val premiumDeferred =
                async {
                    if (needs.premiumExample) {
                        premiumExampleRepository.findByProductIdIn(productIds).toList().map {
                            PremiumExampleItem(
                                gender = it.gender,
                                age = it.age,
                                insurancePeriod = it.insurancePeriod,
                                paymentPeriod = it.paymentPeriod,
                                insuranceAmount = it.insuranceAmount,
                                insuranceAmountText = it.insuranceAmountText,
                                premium = it.premium,
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val indexDeferred =
                async {
                    if (needs.priceIndex) {
                        productIndexRepository.findByProductIdIn(productIds).toList().map {
                            ProductIndexItem(
                                indexType = it.indexType,
                                gender = it.gender,
                                coverageName = it.coverageName,
                                indexValue = it.indexValue,
                                baseDate = it.baseDate,
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val featureDeferred =
                async {
                    if (needs.productFeature) {
                        productFeatureRepository.findByProductIdIn(productIds).toList().map {
                            ProductFeatureItem(key = it.key, value = it.value)
                        }
                    } else {
                        emptyList()
                    }
                }

            val yieldDeferred =
                async {
                    if (needs.yieldScenario) {
                        productYieldScenarioRepository.findByProductIdIn(productIds).toList().map {
                            YieldScenarioItem(
                                scenarioType = it.scenarioType,
                                scenarioLabel = it.scenarioLabel,
                                yieldRate = it.yieldRate,
                                gender = it.gender,
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val metricDeferred =
                async {
                    if (needs.productMetric) {
                        productMetricRepository.findByProductIdIn(productIds).toList().map {
                            ProductMetricItem(
                                metricType = it.metricType,
                                metricValue = it.metricValue,
                                referenceDate = it.referenceDate,
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            SupplementaryData(
                surrenderValues = surrenderDeferred.await(),
                premiumExamples = premiumDeferred.await(),
                priceIndices = indexDeferred.await(),
                productFeatures = featureDeferred.await(),
                yieldScenarios = yieldDeferred.await(),
                productMetrics = metricDeferred.await(),
            )
        }
}
