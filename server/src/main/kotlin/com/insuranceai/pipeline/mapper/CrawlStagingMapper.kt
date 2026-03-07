package com.insuranceai.pipeline.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.entity.CrawlStagingEntity
import io.r2dbc.postgresql.codec.Json
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
            entityData = Json.of(objectMapper.writeValueAsString(product)),
        )

    fun fromStagingEntity(entity: CrawlStagingEntity): ProductRawData =
        objectMapper.readValue(entity.entityData.asArray(), ProductRawData::class.java)

    /** ProductRawData를 JSON으로 직렬화 (기존 staging entity 업데이트용) */
    fun toJson(product: ProductRawData): Json = Json.of(objectMapper.writeValueAsString(product))
}
