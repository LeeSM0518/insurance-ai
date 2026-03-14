package com.insuranceai.common.ai

import com.insuranceai.common.config.LlmRetryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.content.Media
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

/**
 * Spring AI ChatClient를 코루틴 환경에서 사용하기 위한 래퍼.
 * blocking 호출은 Dispatchers.IO로 전환하고, 스트리밍은 Flux를 그대로 반환합니다.
 * 429 Rate Limit 에러 발생 시 지수 백오프로 재시도합니다.
 */
class SuspendingChatClient(
    private val chatClient: ChatClient,
    private val retryConfig: LlmRetryConfig = LlmRetryConfig(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 단일 응답 호출 (코루틴) */
    suspend fun call(
        userMessage: String,
        systemMessage: String? = null,
    ): String =
        withRateLimitRetry {
            withContext(Dispatchers.IO) {
                val response =
                    chatClient
                        .prompt()
                        .apply { systemMessage?.let { system(it) } }
                        .user(userMessage)
                        .call()
                        .chatResponse()
                logUsage("call", response?.metadata?.usage)
                response?.result?.output?.text ?: ""
            }
        }

    /** 구조화된 응답 호출 (코루틴) - JSON을 지정 타입으로 역직렬화 */
    suspend fun <T : Any> callEntity(
        userMessage: String,
        systemMessage: String? = null,
        responseType: Class<T>,
    ): T =
        withRateLimitRetry {
            withContext(Dispatchers.IO) {
                chatClient
                    .prompt()
                    .apply { systemMessage?.let { system(it) } }
                    .user(userMessage)
                    .call()
                    .entity(responseType)!!
            }
        }

    /** 이미지/미디어 포함 호출 (코루틴) */
    suspend fun callWithMedia(
        userMessage: String,
        media: List<Media>,
        systemMessage: String? = null,
    ): String =
        withRateLimitRetry {
            withContext(Dispatchers.IO) {
                val response =
                    chatClient
                        .prompt()
                        .apply { systemMessage?.let { system(it) } }
                        .user { u -> u.text(userMessage).media(*media.toTypedArray()) }
                        .call()
                        .chatResponse()
                logUsage("callWithMedia", response?.metadata?.usage)
                response?.result?.output?.text ?: ""
            }
        }

    /** 스트리밍 응답 (Flux 반환, 429 시 Reactor retryWhen 사용) */
    fun stream(
        userMessage: String,
        systemMessage: String? = null,
    ): Flux<String> {
        val lastUsage = AtomicReference<Usage>()
        return Flux
            .defer {
                chatClient
                    .prompt()
                    .apply { systemMessage?.let { system(it) } }
                    .user(userMessage)
                    .stream()
                    .chatResponse()
                    .doOnNext { response ->
                        response.metadata?.usage?.let { usage ->
                            if (usage.totalTokens > 0) {
                                lastUsage.set(usage)
                            }
                        }
                    }.map { it.result?.output?.text ?: "" }
                    .filter { it.isNotEmpty() }
            }.doOnComplete {
                logUsage("stream", lastUsage.get())
            }.retryWhen(
                Retry
                    .backoff(
                        (retryConfig.maxRetries - 1).toLong(),
                        Duration.ofMillis(retryConfig.initialDelayMs),
                    ).maxBackoff(Duration.ofMillis(retryConfig.maxDelayMs))
                    .jitter(0.2)
                    .filter { isRateLimitError(it) }
                    .doBeforeRetry { signal ->
                        log.warn(
                            "스트리밍 rate limit 재시도 ({}/{}): {}",
                            signal.totalRetries() + 1,
                            retryConfig.maxRetries - 1,
                            signal.failure().message,
                        )
                    },
            )
    }

    /** 429 Rate Limit 에러 여부 판별 */
    internal fun isRateLimitError(e: Throwable): Boolean {
        val message = e.message ?: return false
        return "429" in message || "rate_limit" in message
    }

    /** 429 에러 시 지수 백오프로 재시도하는 래퍼 */
    private suspend fun <T> withRateLimitRetry(action: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..retryConfig.maxRetries) {
            try {
                return action()
            } catch (e: Exception) {
                if (!isRateLimitError(e)) throw e
                lastException = e
                if (attempt < retryConfig.maxRetries) {
                    val delayMs = calculateDelay(attempt)
                    log.warn(
                        "Rate limit 초과, {}ms 후 재시도 ({}/{}): {}",
                        delayMs,
                        attempt,
                        retryConfig.maxRetries,
                        e.message,
                    )
                    delay(delayMs)
                }
            }
        }
        throw lastException!!
    }

    /** 지수 백오프 대기 시간 계산 (±20% jitter 포함) */
    internal fun calculateDelay(attempt: Int): Long {
        val exponentialDelay =
            retryConfig.initialDelayMs *
                retryConfig.multiplier.pow(attempt - 1).toLong()
        val jitter = (exponentialDelay * 0.2 * (Math.random() * 2 - 1)).toLong()
        return (exponentialDelay + jitter).coerceIn(
            retryConfig.initialDelayMs,
            retryConfig.maxDelayMs,
        )
    }

    /** LLM 토큰 사용량 로깅 */
    private fun logUsage(
        method: String,
        usage: Usage?,
    ) {
        if (usage == null) return
        log.info(
            "LLM 토큰 사용량 [{}]: input={}, output={}, total={}",
            method,
            usage.promptTokens,
            usage.completionTokens,
            usage.totalTokens,
        )
    }
}
