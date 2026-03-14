package com.insuranceai.policy.repository

import com.insuranceai.policy.entity.DataSourceEntity
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface DataSourceRepository : CoroutineCrudRepository<DataSourceEntity, UUID> {
    suspend fun findByChecksum(checksum: String): DataSourceEntity?

    /** 같은 상품의 PRODUCT_SUMMARY 출처 중 filePath가 있는 것 조회 */
    @Query(
        """
        SELECT ds.* FROM data_sources ds
        JOIN policy_articles pa ON pa.data_source_id = ds.id
        WHERE pa.product_id = :productId
          AND ds.source_type = 'PRODUCT_SUMMARY'
          AND ds.file_path IS NOT NULL
        LIMIT 1
        """,
    )
    suspend fun findProductSummaryByProductId(productId: UUID): DataSourceEntity?
}
