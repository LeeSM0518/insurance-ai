package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.entity.CrawlHistoryEntity
import com.insuranceai.pipeline.repository.CrawlHistoryRepository
import com.insuranceai.policy.domain.InsuranceType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PipelineServiceTest {
    private val crawlHistoryRepository = mockk<CrawlHistoryRepository>()
    private val service = PipelineService(crawlHistoryRepository)

    @Nested
    inner class StartBatch {
        @Test
        fun `배치 시작 시 이력 저장`() =
            runTest {
                val slot = slot<CrawlHistoryEntity>()
                coEvery { crawlHistoryRepository.save(capture(slot)) } answers {
                    slot.captured.copy(id = UUID.randomUUID())
                }

                service.startBatch(InsuranceType.LIFE, "batch-001")

                assertEquals("batch-001", slot.captured.batchId)
                assertEquals("LIFE", slot.captured.insuranceType)
                assertEquals(0, slot.captured.totalProducts)
            }
    }

    @Nested
    inner class CompleteBatch {
        @Test
        fun `배치 완료 시 카운트 업데이트`() =
            runTest {
                val historyId = UUID.randomUUID()
                val existing =
                    CrawlHistoryEntity(
                        id = historyId,
                        batchId = "batch-001",
                        insuranceType = "LIFE",
                        totalProducts = 0,
                        successCount = 0,
                        failureCount = 0,
                    )

                coEvery { crawlHistoryRepository.findByBatchId("batch-001") } returns existing

                val slot = slot<CrawlHistoryEntity>()
                coEvery { crawlHistoryRepository.save(capture(slot)) } answers {
                    slot.captured
                }

                service.completeBatch("batch-001", 100, 90, 10)

                assertEquals(100, slot.captured.totalProducts)
                assertEquals(90, slot.captured.successCount)
                assertEquals(10, slot.captured.failureCount)
            }

        @Test
        fun `존재하지 않는 배치 ID면 에러`() =
            runTest {
                coEvery { crawlHistoryRepository.findByBatchId("unknown") } returns null

                assertFailsWith<IllegalStateException> {
                    service.completeBatch("unknown", 0, 0, 0)
                }
            }
    }

    @Nested
    inner class FailBatch {
        @Test
        fun `배치 실패 시 batchHeld 설정`() =
            runTest {
                val existing =
                    CrawlHistoryEntity(
                        id = UUID.randomUUID(),
                        batchId = "batch-002",
                        insuranceType = "NON_LIFE",
                        totalProducts = 0,
                        successCount = 0,
                        failureCount = 0,
                    )

                coEvery { crawlHistoryRepository.findByBatchId("batch-002") } returns existing

                val slot = slot<CrawlHistoryEntity>()
                coEvery { crawlHistoryRepository.save(capture(slot)) } answers { slot.captured }

                service.failBatch("batch-002", "크롤링 실패")

                assertEquals(true, slot.captured.batchHeld)
            }
    }

    @Nested
    inner class GetRecentBatches {
        @Test
        fun `최근 배치 이력 조회`() =
            runTest {
                val batch1 =
                    CrawlHistoryEntity(
                        id = UUID.randomUUID(),
                        batchId = "b1",
                        insuranceType = "LIFE",
                        totalProducts = 10,
                        successCount = 10,
                        failureCount = 0,
                    )

                coEvery {
                    crawlHistoryRepository.findByInsuranceTypeOrderByCreatedAtDesc("LIFE")
                } returns flowOf(batch1)

                val result = service.getRecentBatches(InsuranceType.LIFE, 5)

                assertEquals(1, result.size)
                assertEquals("b1", result[0].batchId)
            }
    }
}
