package com.insuranceai.report.service

import com.insuranceai.report.dto.CombinedComparisonData
import com.insuranceai.report.dto.CompanyComparison
import com.insuranceai.report.dto.ComparisonCategoryInfo
import com.insuranceai.report.dto.ComparisonRequest
import com.insuranceai.report.dto.ComparisonResponse
import com.insuranceai.report.dto.CoverageItem
import com.insuranceai.report.dto.ProductComparison
import com.insuranceai.report.repository.ComparisonJooqRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ComparisonService(
    private val comparisonJooqRepository: ComparisonJooqRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 비교 데이터 조회 */
    suspend fun compareProducts(request: ComparisonRequest): ComparisonResponse {
        // 1. 보험사별 상품 조회
        val productsByCompany =
            comparisonJooqRepository.queryProductsByCompany(
                request.companyIds,
                request.category,
                request.subcategory,
            )

        // 2. 전체 상품 ID 수집
        val allProductIds = productsByCompany.values.flatten().map { it.id }

        // 3. 병렬 데이터 조회
        val (coveragesMap, exemptionsMap, premiumsMap, surrendersMap) =
            coroutineScope {
                val coverages = async { comparisonJooqRepository.queryCoveragesByProducts(allProductIds) }
                val exemptions = async { comparisonJooqRepository.queryExemptionsByProducts(allProductIds) }
                val premiums = async { comparisonJooqRepository.queryPremiumExamples(allProductIds, request.age, request.gender) }
                val surrenders = async { comparisonJooqRepository.querySurrenderSamples(allProductIds) }
                CombinedComparisonData(coverages.await(), exemptions.await(), premiums.await(), surrenders.await())
            }

        // 4. 공통 비교 항목 추출
        val comparisonItems = extractCommonCoverageNames(coveragesMap)

        // 5. 응답 구성
        val companies =
            productsByCompany.map { (companyInfo, products) ->
                CompanyComparison(
                    companyId = companyInfo.id,
                    companyName = companyInfo.name,
                    insuranceType = companyInfo.type,
                    products =
                        products.map { product ->
                            ProductComparison(
                                productId = product.id,
                                productName = product.name,
                                renewalType = product.renewalType,
                                coverages = coveragesMap[product.id] ?: emptyList(),
                                exemptions = exemptionsMap[product.id] ?: emptyList(),
                                premiumExample = premiumsMap[product.id],
                                surrenderValueSample = surrendersMap[product.id],
                                insurancePeriod = product.insurancePeriod,
                            )
                        },
                )
            }

        logger.info("비교 조회 완료: category=${request.category}, companies=${companies.size}, products=${allProductIds.size}")

        return ComparisonResponse(
            category = request.category,
            companies = companies,
            comparisonItems = comparisonItems,
        )
    }

    /** 비교 가능한 카테고리 목록 조회 */
    suspend fun getAvailableCategories(): List<ComparisonCategoryInfo> = comparisonJooqRepository.queryAvailableCategories()

    /**
     * 공통 비교 항목 추출:
     * 2개 이상 상품에 공통으로 존재하는 COVERAGE 항목명 추출
     */
    private fun extractCommonCoverageNames(coveragesMap: Map<UUID, List<CoverageItem>>): List<String> {
        val nameFrequency = mutableMapOf<String, Int>()
        coveragesMap.values.forEach { coverages ->
            coverages.map { normalizeCoverageName(it.name) }.distinct().forEach { name ->
                nameFrequency[name] = (nameFrequency[name] ?: 0) + 1
            }
        }
        return nameFrequency
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    /** 보장 항목명 정규화 */
    internal fun normalizeCoverageName(name: String): String =
        name
            .replace(Regex("^\\s*(무배당|유배당|\\(무\\)|\\(유\\))\\s*"), "")
            .replace(Regex("^\\s*\\(갱신형\\)\\s*"), "")
            .replace(Regex("^\\s*\\[\\d+세갱신\\]\\s*"), "")
            .replace(Regex("\\s*(특약|보장|담보|\\(선택\\)|\\(필수\\))\\s*$"), "")
            .trim()
}
