package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.ProductRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChangeDetectionService(
    private val productRepository: ProductRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class ChangeDetectionResult(
        val newProducts: List<ProductRawData>,
        val changedProducts: List<ProductRawData>,
        val deletedProductIds: List<UUID>,
    )

    suspend fun detectChanges(
        crawledProducts: List<ProductRawData>,
        insuranceType: InsuranceType,
    ): ChangeDetectionResult {
        // 해당 보험 유형의 보험사 목록 조회
        val companies = insuranceCompanyRepository.findByType(insuranceType.name).toList()
        val companyIds = companies.mapNotNull { it.id }

        // 기존 활성 상품 조회
        val existingProducts =
            companyIds.flatMap { companyId ->
                productRepository.findByInsuranceCompanyIdAndIsActiveTrue(companyId).toList()
            }

        // 기존 상품 맵 (보험사명+상품명 -> Entity)
        val existingMap =
            existingProducts.associateBy { product ->
                val company = companies.first { it.id == product.insuranceCompanyId }
                "${company.name}|${product.name}"
            }

        // 크롤링 결과 맵
        val crawledMap = crawledProducts.associateBy { "${it.companyName}|${it.productName}" }

        // 신규: DB에 없는 상품
        val newProducts =
            crawledProducts.filter { product ->
                "${product.companyName}|${product.productName}" !in existingMap
            }

        // 변경: checksum이 다른 상품
        val changedProducts =
            crawledProducts.filter { product ->
                val key = "${product.companyName}|${product.productName}"
                val existing = existingMap[key]
                existing != null && existing.checksum != product.checksum
            }

        // 삭제: 크롤링 결과에 없는 기존 활성 상품
        val deletedProductIds =
            existingMap
                .filter { (key, _) -> key !in crawledMap }
                .mapNotNull { (_, entity) -> entity.id }

        logger.info(
            "변경 감지 결과: 신규 ${newProducts.size}건, 변경 ${changedProducts.size}건, 삭제 ${deletedProductIds.size}건",
        )

        return ChangeDetectionResult(
            newProducts = newProducts,
            changedProducts = changedProducts,
            deletedProductIds = deletedProductIds,
        )
    }
}
