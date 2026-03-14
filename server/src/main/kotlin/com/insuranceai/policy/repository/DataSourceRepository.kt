package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.DataSourceEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface DataSourceRepository : CoroutineCrudRepository<DataSourceEntity, UUID> {
    suspend fun findByChecksum(checksum: String): DataSourceEntity?

    /** 상품의 PRODUCT_SUMMARY PDF data_source 직접 조회 */
    @Query(
        """
        SELECT * FROM data_sources
        WHERE product_id = :productId
          AND source_type = 'PRODUCT_SUMMARY'
          AND file_path IS NOT NULL
        LIMIT 1
        """,
    )
    suspend fun findProductSummaryByProductId(productId: UUID): DataSourceEntity?
}
