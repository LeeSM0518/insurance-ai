package com.insuranceai.pipeline.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.entity.CrawlStagingEntity
import org.springframework.stereotype.Component

@Component
class CrawlStagingMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toStagingEntity(
        product: ProductRawData,
        batchId: String,
    ): CrawlStagingEntity =
        CrawlStagingEntity(
            batchId = batchId,
            entityType = "PRODUCT",
            entityData = objectMapper.writeValueAsString(product),
        )

    fun fromStagingEntity(entity: CrawlStagingEntity): ProductRawData =
        objectMapper.readValue(entity.entityData, ProductRawData::class.java)
}
