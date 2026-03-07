package com.insuranceai.pipeline.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.insuranceai.policy.domain.InsuranceType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class CrawlDtosTest {
    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())

    @Nested
    inner class CoverageDataSerialization {
        @Test
        fun `직렬화 역직렬화`() {
            val data = CoverageData("암진단", "암 진단 시", "3000만원", "주계약")
            val json = objectMapper.writeValueAsString(data)
            val restored = objectMapper.readValue(json, CoverageData::class.java)
            assertEquals(data, restored)
        }
    }

    @Nested
    inner class PremiumDataSerialization {
        @Test
        fun `직렬화 역직렬화`() {
            val data = PremiumData("남", 40, "80세만기", "20년납", 30000000, 85000)
            val json = objectMapper.writeValueAsString(data)
            val restored = objectMapper.readValue(json, PremiumData::class.java)
            assertEquals(data, restored)
        }

        @Test
        fun `age nullable 직렬화 역직렬화`() {
            val data = PremiumData("남", null, "10년만기", "전기납", 50000000, 120000)
            val json = objectMapper.writeValueAsString(data)
            val restored = objectMapper.readValue(json, PremiumData::class.java)
            assertEquals(data, restored)
            assertEquals(null, restored.age)
        }
    }

    @Nested
    inner class IndexDataSerialization {
        @Test
        fun `직렬화 역직렬화`() {
            val data = IndexData("PRICE", "남", null, BigDecimal("95.3"), LocalDate.of(2026, 1, 1))
            val json = objectMapper.writeValueAsString(data)
            val restored = objectMapper.readValue(json, IndexData::class.java)
            assertEquals(data.indexType, restored.indexType)
            assertEquals(data.gender, restored.gender)
            assertEquals(0, data.value.compareTo(restored.value))
        }
    }

    @Nested
    inner class MetricDataSerialization {
        @Test
        fun `직렬화 역직렬화`() {
            val data = MetricData("LOSS_RATIO", BigDecimal("78.5"), LocalDate.of(2026, 1, 1))
            val json = objectMapper.writeValueAsString(data)
            val restored = objectMapper.readValue(json, MetricData::class.java)
            assertEquals(data.metricType, restored.metricType)
            assertEquals(0, data.value.compareTo(restored.value))
        }
    }

    @Nested
    inner class CrawlResultSerialization {
        @Test
        fun `직렬화 역직렬화`() {
            val result =
                CrawlResult(
                    insuranceType = InsuranceType.LIFE,
                    totalProducts = 1,
                    successCount = 1,
                    failureCount = 0,
                    products =
                        listOf(
                            ProductRawData(
                                companyName = "삼성생명",
                                productName = "테스트상품",
                                category = "종신보험",
                                checksum = "abc123",
                            ),
                        ),
                    errors = emptyList(),
                )
            val json = objectMapper.writeValueAsString(result)
            val restored = objectMapper.readValue(json, CrawlResult::class.java)
            assertEquals(result.insuranceType, restored.insuranceType)
            assertEquals(result.totalProducts, restored.totalProducts)
            assertEquals(result.products.size, restored.products.size)
        }
    }

    @Nested
    inner class CrawlErrorSerialization {
        @Test
        fun `직렬화 역직렬화`() {
            val error = CrawlError("종신보험", "삼성생명", "타임아웃", "NETWORK")
            val json = objectMapper.writeValueAsString(error)
            val restored = objectMapper.readValue(json, CrawlError::class.java)
            assertEquals(error, restored)
        }
    }
}
