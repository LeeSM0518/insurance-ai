package com.insuranceai.pipeline.service

import com.insuranceai.pipeline.parser.ComparisonDataParser.PremiumConditions
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 특이사항 텍스트에서 보험료 비교 기준 조건을 LLM으로 추출하는 서비스.
 *
 * Claude Haiku + Spring AI Structured Output으로 한국어 문맥을 이해하여
 * age, insurancePeriod, paymentPeriod를 구조화된 데이터로 반환합니다.
 */
@Service
class NotesExtractionService(
    @param:Qualifier("pipelineChatClient") private val chatClient: ChatClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val SYSTEM_PROMPT =
            """보험 비교공시 데이터의 "특이사항" 텍스트에서 보험료 비교 기준 조건을 추출합니다.

## 추출 규칙
- age: 보험료 기준에 명시된 나이 (정수). "N세만기"는 보험기간이므로 나이가 아님.
  재물보험(화재/종합)은 나이가 없을 수 있음 → null
- insurancePeriod: "종신", "N년만기", "N세만기", "N년" 중 하나
- paymentPeriod: "N년납", "전기납", "일시납" 중 하나
- "보험료 기준"과 "보험가격지수 기준"이 모두 있으면 "보험료 기준"의 값을 사용
- 괄호 안의 조건(예: "치아관련특약 20년만기 전기납")은 무시하고, 메인 기준만 추출

## 예시

입력: "보험료 기준 : 2종, 가입금액 1억원, 40세, 20년납, 월납\n보험기간 : 종신"
출력: {"age": 40, "insurancePeriod": "종신", "paymentPeriod": "20년납"}

입력: "ㆍ40세, 상해1급, 100세만기 20년납 기준"
출력: {"age": 40, "insurancePeriod": "100세만기", "paymentPeriod": "20년납"}

입력: "ㆍ아파트, 건물1급, 100㎡, 10년만기 전기납 기준"
출력: {"age": null, "insurancePeriod": "10년만기", "paymentPeriod": "전기납"}

입력: "보험가격지수 기준 : 40세, 110세만기, 20년납 보험료 기준 : 40세, 110세만기, 5년납"
출력: {"age": 40, "insurancePeriod": "110세만기", "paymentPeriod": "5년납"}

입력: "보험료 예시기준 : 100세만기, 5세, 10년납, 월납"
출력: {"age": 5, "insurancePeriod": "100세만기", "paymentPeriod": "10년납"}

입력: "※ 100세만기 5년납, 월납, 40세 기준"
출력: {"age": 40, "insurancePeriod": "100세만기", "paymentPeriod": "5년납"}

반드시 JSON만 출력하세요. 마크다운 코드블록 없이 순수 JSON만 출력하세요."""
    }

    // 동일 특이사항 텍스트 → 동일 결과 캐싱
    private val cache = ConcurrentHashMap<String, PremiumConditions>()

    /**
     * 특이사항 텍스트에서 보험료 비교 기준 조건 추출
     */
    fun extractConditions(notes: String): PremiumConditions =
        cache.getOrPut(notes) {
            logger.debug("LLM 특이사항 추출 요청: ${notes.take(50)}...")
            chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user("입력: \"$notes\"")
                .call()
                .entity(PremiumConditions::class.java)
                ?: throw IllegalStateException("LLM 응답 파싱 실패: $notes")
        }

    /** 캐시 초기화 (테스트 용도) */
    fun clearCache() {
        cache.clear()
    }
}
