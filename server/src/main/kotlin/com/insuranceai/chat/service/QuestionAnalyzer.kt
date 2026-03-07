package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * LLM 기반 질문 분석기.
 * SuspendingChatClient.callEntity()로 Structured Output을 호출하여
 * 보험사명, 카테고리, 내용유형, 비교 의도, 보충 데이터 필요 여부를 추출합니다.
 */
@Service
class QuestionAnalyzer(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var companyNames: List<String> = emptyList()

    @EventListener(ApplicationReadyEvent::class)
    fun loadCompanyNames() {
        companyNames =
            runBlocking {
                insuranceCompanyRepository.findByIsActiveTrue().toList().map { it.name }
            }
        log.info("보험사 목록 캐시 로드: ${companyNames.size}개")
    }

    suspend fun analyze(message: String): QuestionAnalysis {
        val systemPrompt =
            promptLoader.render(
                "chat/question-analysis.st",
                mapOf("companyNames" to companyNames.joinToString(", ")),
            )
        return try {
            suspendingChatClient.callEntity(
                userMessage = message,
                systemMessage = systemPrompt,
                responseType = QuestionAnalysis::class.java,
            )
        } catch (e: Exception) {
            log.warn("LLM 질문 분석 실패, 기본값 반환: ${e.message}")
            QuestionAnalysis(
                companies = emptyList(),
                categories = emptyList(),
                contentTypes = emptyList(),
                isComparison = false,
                needsSupplementary = SupplementaryNeeds(),
            )
        }
    }
}
