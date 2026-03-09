package com.insuranceai.pipeline.embedder

import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.PolicyEmbeddingEntity
import com.insuranceai.policy.repository.PolicyEmbeddingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.EmbeddingResultMetadata
import java.util.UUID
import kotlin.test.assertEquals

class EmbeddingServiceTest {
    private val embeddingModel = mockk<EmbeddingModel>()
    private val policyEmbeddingRepository = mockk<PolicyEmbeddingRepository>()
    private val service = EmbeddingService(embeddingModel, policyEmbeddingRepository)

    private val testVector = floatArrayOf(0.1f, 0.2f, 0.3f)

    private fun mockEmbeddingResponse(vectors: List<FloatArray>): EmbeddingResponse {
        val results =
            vectors.mapIndexed { index, vector ->
                org.springframework.ai.embedding
                    .Embedding(vector, index, EmbeddingResultMetadata.EMPTY)
            }
        return EmbeddingResponse(results)
    }

    @Nested
    inner class Embed {
        @Test
        fun `단일 텍스트 임베딩 호출`() =
            runTest {
                every { embeddingModel.call(any<EmbeddingRequest>()) } returns
                    mockEmbeddingResponse(listOf(testVector))

                val result = service.embed("테스트 텍스트")

                assertEquals(3, result.size)
                assertEquals(0.1f, result[0])
            }
    }

    @Nested
    inner class EmbedBatch {
        @Test
        fun `빈 목록이면 빈 결과 반환`() =
            runTest {
                val result = service.embedBatch(emptyList())
                assertEquals(0, result.size)
            }

        @Test
        fun `배치 임베딩 호출`() =
            runTest {
                val vectors = listOf(floatArrayOf(0.1f), floatArrayOf(0.2f))
                every { embeddingModel.call(any<EmbeddingRequest>()) } returns
                    mockEmbeddingResponse(vectors)

                val result = service.embedBatch(listOf("텍스트1", "텍스트2"))

                assertEquals(2, result.size)
            }
    }

    @Nested
    inner class EmbedAndSave {
        @Test
        fun `기존 임베딩 없으면 새로 저장`() =
            runTest {
                val articleId = UUID.randomUUID()
                every { embeddingModel.call(any<EmbeddingRequest>()) } returns
                    mockEmbeddingResponse(listOf(testVector))
                coEvery { policyEmbeddingRepository.findByPolicyArticleId(articleId) } returns null
                coEvery { policyEmbeddingRepository.saveWithVector(any()) } answers {
                    firstArg<PolicyEmbeddingEntity>().copy(id = UUID.randomUUID())
                }

                val result = service.embedAndSave(articleId, "테스트 내용")

                coVerify(exactly = 0) { policyEmbeddingRepository.deleteById(any()) }
                coVerify(exactly = 1) { policyEmbeddingRepository.saveWithVector(any()) }
            }

        @Test
        fun `기존 임베딩 있으면 삭제 후 저장`() =
            runTest {
                val articleId = UUID.randomUUID()
                val existingId = UUID.randomUUID()
                val existing = PolicyEmbeddingEntity(id = existingId, policyArticleId = articleId, embedding = testVector)

                every { embeddingModel.call(any<EmbeddingRequest>()) } returns
                    mockEmbeddingResponse(listOf(testVector))
                coEvery { policyEmbeddingRepository.findByPolicyArticleId(articleId) } returns existing
                coEvery { policyEmbeddingRepository.deleteById(existingId) } returns Unit
                coEvery { policyEmbeddingRepository.saveWithVector(any()) } answers {
                    firstArg<PolicyEmbeddingEntity>().copy(id = UUID.randomUUID())
                }

                service.embedAndSave(articleId, "테스트 내용")

                coVerify(exactly = 1) { policyEmbeddingRepository.deleteById(existingId) }
                coVerify(exactly = 1) { policyEmbeddingRepository.saveWithVector(any()) }
            }
    }

    @Nested
    inner class EmbedArticles {
        @Test
        fun `빈 목록이면 0 반환`() =
            runTest {
                val result = service.embedArticles(emptyList())
                assertEquals(0, result)
            }

        @Test
        fun `여러 조문 배치 임베딩 + 저장`() =
            runTest {
                val articleId1 = UUID.randomUUID()
                val articleId2 = UUID.randomUUID()
                val articles =
                    listOf(
                        PolicyArticleEntity(id = articleId1, productId = UUID.randomUUID(), content = "내용1", contentType = "COVERAGE"),
                        PolicyArticleEntity(id = articleId2, productId = UUID.randomUUID(), content = "내용2", contentType = "EXEMPTION"),
                    )

                val vectors = listOf(floatArrayOf(0.1f), floatArrayOf(0.2f))
                every { embeddingModel.call(any<EmbeddingRequest>()) } returns
                    mockEmbeddingResponse(vectors)
                coEvery { policyEmbeddingRepository.findByPolicyArticleId(any()) } returns null
                coEvery { policyEmbeddingRepository.saveWithVector(any()) } answers {
                    firstArg<PolicyEmbeddingEntity>().copy(id = UUID.randomUUID())
                }

                val result = service.embedArticles(articles)

                assertEquals(2, result)
                coVerify(exactly = 2) { policyEmbeddingRepository.saveWithVector(any()) }
            }
    }
}
