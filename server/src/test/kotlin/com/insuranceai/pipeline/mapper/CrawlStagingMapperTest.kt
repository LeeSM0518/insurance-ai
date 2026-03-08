package com.insuranceai.pipeline.mapper

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.MetricData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CrawlStagingMapperTest {
    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val mapper = CrawlStagingMapper(objectMapper)

    @Nested
    inner class ToStagingEntity {
        @Test
        fun `ProductRawData를 CrawlStagingEntity로 변환`() {
            val product = createFullProductRawData()
            val entity = mapper.toStagingEntity(product, "batch-123")

            assertEquals("batch-123", entity.batchId)
            assertEquals("PRODUCT", entity.entityType)
            assertNotNull(entity.entityData)
        }
    }

    @Nested
    inner class FromStagingEntity {
        @Test
        fun `CrawlStagingEntity에서 ProductRawData 복원`() {
            val original = createFullProductRawData()
            val entity = mapper.toStagingEntity(original, "batch-123")
            val restored = mapper.fromStagingEntity(entity)

            assertEquals(original.companyName, restored.companyName)
            assertEquals(original.productName, restored.productName)
            assertEquals(original.category, restored.category)
            assertEquals(original.checksum, restored.checksum)
        }
    }

    @Nested
    inner class RoundTrip {
        @Test
        fun `전체 필드 직렬화 역직렬화`() {
            val original = createFullProductRawData()
            val entity = mapper.toStagingEntity(original, "batch-456")
            val restored = mapper.fromStagingEntity(entity)

            assertEquals(original.coverages.size, restored.coverages.size)
            assertEquals(original.premiumExamples.size, restored.premiumExamples.size)
            assertEquals(original.indices.size, restored.indices.size)
            assertEquals(original.metrics.size, restored.metrics.size)
            assertEquals(original.remarks, restored.remarks)
            assertEquals(original.subscriptionType, restored.subscriptionType)
        }
    }

    private fun createFullProductRawData() =
        ProductRawData(
            companyName = "삼성생명",
            productName = "무배당 건강보험",
            category = "질병보험",
            subcategory = "건강보험",
            renewalType = "비갱신형",
            salesChannel = "대면",
            coverages =
                listOf(
                    CoverageData("암진단", "암 진단 시", "3000만원", "주계약"),
                    CoverageData("수술비", "수술 시", "100만원", "특약"),
                ),
            premiumExamples =
                listOf(
                    PremiumData("남", 40, "80세만기", "20년납", 30000000, premium = 85000),
                ),
            indices =
                listOf(
                    IndexData("PRICE", null, null, BigDecimal("95.3"), LocalDate.of(2026, 1, 1)),
                ),
            metrics =
                listOf(
                    MetricData("LOSS_RATIO", BigDecimal("78.5"), LocalDate.of(2026, 1, 1)),
                ),
            checksum = "abc123def456",
            remarks = "특이사항 없음",
            subscriptionType = "표준형",
            copaymentRate = BigDecimal("0.20"),
            sourceUrl = "https://pub.insure.or.kr/test",
        )
}
