package com.insuranceai.pipeline.embedder

import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.entity.PolicyEmbeddingEntity
import com.insuranceai.policy.repository.PolicyEmbeddingRepository
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import com.knuddels.jtokkit.api.IntArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EmbeddingService(
    private val embeddingModel: EmbeddingModel,
    private val policyEmbeddingRepository: PolicyEmbeddingRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)

    companion object {
        const val MODEL_NAME = "text-embedding-3-large"
        const val BATCH_SIZE = 100
        const val MAX_RETRIES = 3
        const val INITIAL_DELAY_MS = 1000L
        const val MAX_INPUT_TOKENS = 8000
    }

    /** 토큰 수 기준으로 텍스트 truncate */
    private fun truncateText(text: String): String {
        val tokens = encoding.encode(text)
        if (tokens.size() <= MAX_INPUT_TOKENS) return text
        logger.warn("토큰 길이 초과로 truncate: ${tokens.size()}토큰 → ${MAX_INPUT_TOKENS}토큰")
        val truncated = IntArrayList(MAX_INPUT_TOKENS)
        for (i in 0 until MAX_INPUT_TOKENS) {
            truncated.add(tokens.get(i))
        }
        return encoding.decode(truncated)
    }

    /** 단일 텍스트 임베딩 */
    suspend fun embed(text: String): FloatArray =
        withRetry {
            withContext(Dispatchers.IO) {
                val request =
                    EmbeddingRequest(
                        listOf(truncateText(text)),
                        OpenAiEmbeddingOptions
                            .builder()
                            .model(MODEL_NAME)
                            .build(),
                    )
                val response = embeddingModel.call(request)
                response.results[0].output
            }
        }

    /** 배치 임베딩 (최대 BATCH_SIZE건씩 분할) */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val truncatedTexts = texts.map { truncateText(it) }
        val results = mutableListOf<FloatArray>()
        for (chunk in truncatedTexts.chunked(BATCH_SIZE)) {
            val chunkResults =
                withRetry {
                    withContext(Dispatchers.IO) {
                        val request =
                            EmbeddingRequest(
                                chunk,
                                OpenAiEmbeddingOptions
                                    .builder()
                                    .model(MODEL_NAME)
                                    .build(),
                            )
                        val response = embeddingModel.call(request)
                        response.results.map { it.output }
                    }
                }
            results.addAll(chunkResults)
        }
        return results
    }

    /** 단일 조문 임베딩 + 저장 (upsert 패턴) */
    suspend fun embedAndSave(
        policyArticleId: UUID,
        content: String,
    ): PolicyEmbeddingEntity {
        val vector = embed(content)

        // 기존 임베딩이 있으면 삭제 후 새로 저장
        val existing = policyEmbeddingRepository.findByPolicyArticleId(policyArticleId)
        if (existing != null) {
            policyEmbeddingRepository.deleteById(existing.id!!)
        }

        return policyEmbeddingRepository.saveWithVector(
            PolicyEmbeddingEntity(
                policyArticleId = policyArticleId,
                embedding = vector,
                embeddingModel = MODEL_NAME,
            ),
        )
    }

    /** 여러 조문 배치 임베딩 + 저장 */
    suspend fun embedArticles(articles: List<PolicyArticleEntity>): Int {
        if (articles.isEmpty()) return 0

        val contents = articles.map { it.content }
        val vectors = embedBatch(contents)

        var savedCount = 0
        articles.zip(vectors).forEach { (article, vector) ->
            val articleId = article.id ?: return@forEach
            val existing = policyEmbeddingRepository.findByPolicyArticleId(articleId)
            if (existing != null) {
                policyEmbeddingRepository.deleteById(existing.id!!)
            }
            policyEmbeddingRepository.saveWithVector(
                PolicyEmbeddingEntity(
                    policyArticleId = articleId,
                    embedding = vector,
                    embeddingModel = MODEL_NAME,
                ),
            )
            savedCount++
        }

        logger.info("임베딩 완료: ${savedCount}건 저장")
        return savedCount
    }

    /** 지수 백오프 재시도 */
    private suspend fun <T> withRetry(action: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    val delayMs = INITIAL_DELAY_MS * (1L shl (attempt - 1))
                    logger.warn("임베딩 API 호출 실패 (시도 $attempt/$MAX_RETRIES), ${delayMs}ms 후 재시도: ${e.message}")
                    delay(delayMs)
                }
            }
        }
        throw lastException!!
    }
}
