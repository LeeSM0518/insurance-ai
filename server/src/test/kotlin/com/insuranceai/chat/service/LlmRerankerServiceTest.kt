package com.insuranceai.chat.service

import com.insuranceai.chat.dto.RerankResult
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.common.config.AppProperties
import com.insuranceai.common.config.ChatProperties
import com.insuranceai.common.config.KakaoProperties
import com.insuranceai.common.config.RateLimitProperties
import com.insuranceai.common.config.RerankingProperties
import com.insuranceai.common.config.SearchProperties
import com.insuranceai.search.dto.FusedSearchResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class LlmRerankerServiceTest {
    private val suspendingChatClient = mockk<SuspendingChatClient>()
    private val promptLoader =
        mockk<PromptLoader> {
            every { render(any(), any()) } returns "시스템 프롬프트"
        }

    private fun createService(
        enabled: Boolean = true,
        topN: Int = 5,
    ): LlmRerankerService {
        val appProperties =
            AppProperties(
                kakao = KakaoProperties(),
                search = SearchProperties(reranking = RerankingProperties(enabled = enabled, topN = topN)),
                chat = ChatProperties(),
                rateLimit = RateLimitProperties(),
            )
        return LlmRerankerService(suspendingChatClient, promptLoader, appProperties)
    }

    private fun createDoc(index: Int): FusedSearchResult =
        FusedSearchResult(
            articleId = UUID.randomUUID(),
            productId = UUID.randomUUID(),
            productName = "보험 $index",
            companyId = UUID.randomUUID(),
            companyName = "보험사 $index",
            insuranceType = "LIFE",
            contentType = "COVERAGE",
            articleNumber = null,
            articleTitle = "조문 $index",
            content = "내용 $index",
            dataSource = null,
            relevanceScore = 1.0f - index * 0.1f,
            keywordRank = index,
            vectorRank = null,
        )

    @Test
    fun `documents 크기가 topN 이하이면 LLM 호출 없이 그대로 반환`() =
        runTest {
            val service = createService(topN = 5)
            val docs = (1..3).map { createDoc(it) }

            val result = service.rerank("질문", docs, 5)

            assertEquals(3, result.size)
        }

    @Test
    fun `enabled=false이면 LLM 호출 없이 topN개 반환`() =
        runTest {
            val service = createService(enabled = false, topN = 3)
            val docs = (1..10).map { createDoc(it) }

            val result = service.rerank("질문", docs, 3)

            assertEquals(3, result.size)
            // 원본 순서 유지
            assertEquals(docs[0].articleId, result[0].articleId)
        }

    @Test
    fun `LLM 호출 성공 시 선택된 인덱스 순서로 반환`() =
        runTest {
            val service = createService(topN = 3)
            val docs = (0..9).map { createDoc(it) }

            coEvery {
                suspendingChatClient.callEntity(any(), any(), RerankResult::class.java)
            } returns RerankResult(selectedIndices = listOf(5, 2, 8))

            val result = service.rerank("질문", docs, 3)

            assertEquals(3, result.size)
            assertEquals(docs[5].articleId, result[0].articleId)
            assertEquals(docs[2].articleId, result[1].articleId)
            assertEquals(docs[8].articleId, result[2].articleId)
        }

    @Test
    fun `LLM 호출 실패 시 원본 순서로 폴백`() =
        runTest {
            val service = createService(topN = 3)
            val docs = (0..9).map { createDoc(it) }

            coEvery {
                suspendingChatClient.callEntity(any(), any(), RerankResult::class.java)
            } throws RuntimeException("API 에러")

            val result = service.rerank("질문", docs, 3)

            assertEquals(3, result.size)
            // 원본 순서 유지
            assertEquals(docs[0].articleId, result[0].articleId)
        }

    @Test
    fun `유효하지 않은 인덱스는 무시된다`() =
        runTest {
            val service = createService(topN = 5)
            val docs = (0..9).map { createDoc(it) }

            coEvery {
                suspendingChatClient.callEntity(any(), any(), RerankResult::class.java)
            } returns RerankResult(selectedIndices = listOf(1, 99, -1, 3, 100))

            val result = service.rerank("질문", docs, 5)

            // 유효한 인덱스 1, 3만 반환
            assertEquals(2, result.size)
            assertEquals(docs[1].articleId, result[0].articleId)
            assertEquals(docs[3].articleId, result[1].articleId)
        }

    @Test
    fun `LLM이 빈 배열 반환 시 원본 순서로 폴백`() =
        runTest {
            val service = createService(topN = 3)
            val docs = (0..9).map { createDoc(it) }

            coEvery {
                suspendingChatClient.callEntity(any(), any(), RerankResult::class.java)
            } returns RerankResult(selectedIndices = emptyList())

            val result = service.rerank("질문", docs, 3)

            assertEquals(3, result.size)
            assertEquals(docs[0].articleId, result[0].articleId)
        }
}
