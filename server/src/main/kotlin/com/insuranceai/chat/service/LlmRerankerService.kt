package com.insuranceai.chat.service

import com.insuranceai.chat.dto.RerankResult
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.common.config.AppProperties
import com.insuranceai.search.dto.FusedSearchResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LlmRerankerService(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
    private val appProperties: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * LLM 기반 Listwise Reranking.
     * 하이브리드 검색 결과를 LLM이 질문 관련성 기준으로 재정렬합니다.
     */
    suspend fun rerank(
        query: String,
        documents: List<FusedSearchResult>,
        topN: Int = appProperties.search.reranking.topN,
    ): List<FusedSearchResult> {
        if (!appProperties.search.reranking.enabled || documents.size <= topN) {
            return documents.take(topN)
        }

        val documentList =
            documents
                .mapIndexed { idx, doc ->
                    "[$idx] ${doc.companyName} / ${doc.productName} / ${doc.articleTitle ?: ""} | ${doc.content.take(200)}"
                }.joinToString("\n")

        return try {
            val systemPrompt =
                promptLoader.render(
                    "chat/rerank.st",
                    mapOf("topN" to topN),
                )

            val result =
                suspendingChatClient.callEntity(
                    userMessage = "질문: $query\n\n문서 목록:\n$documentList",
                    systemMessage = systemPrompt,
                    responseType = RerankResult::class.java,
                )

            val reranked =
                result.selectedIndices
                    .filter { it in documents.indices }
                    .take(topN)
                    .map { documents[it] }

            if (reranked.isEmpty()) {
                log.warn("LLM Reranking 결과가 비어있음, 원본 순서 유지")
                documents.take(topN)
            } else {
                log.info("LLM Reranking 완료: {} → {}개 선택", documents.size, reranked.size)
                reranked
            }
        } catch (e: Exception) {
            log.warn("LLM Reranking 실패, 원본 순서 유지: {}", e.message)
            documents.take(topN)
        }
    }
}
