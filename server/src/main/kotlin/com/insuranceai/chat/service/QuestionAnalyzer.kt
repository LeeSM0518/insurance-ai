package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * 규칙 기반 질문 분석기 (Phase 1).
 * 보험사명, 카테고리, 내용유형, 비교 의도, 보충 데이터 필요 여부를 키워드 매칭으로 추출합니다.
 */
@Service
class QuestionAnalyzer(
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 인메모리 캐시: 보험사 이름 목록
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
        val companies = extractCompanies(message)
        val categories = extractCategories(message)
        val contentTypes = extractContentTypes(message)
        val isComparison = detectComparison(message)
        val needsSupplementary = detectSupplementaryNeeds(message)

        return QuestionAnalysis(
            companies = companies,
            categories = categories,
            contentTypes = contentTypes,
            isComparison = isComparison,
            needsSupplementary = needsSupplementary,
        )
    }

    /** 보험사 추출: 캐시된 보험사명 목록과 매칭 */
    internal fun extractCompanies(message: String): List<String> = companyNames.filter { message.contains(it) }

    /** 카테고리 추출 */
    internal fun extractCategories(message: String): List<String> {
        val categoryKeywords =
            mapOf(
                "CANCER" to listOf("암", "암보험"),
                "WHOLE_LIFE" to listOf("종신", "종신보험"),
                "HEALTH" to listOf("실손", "실비", "의료실비"),
                "DENTAL" to listOf("치아", "치아보험"),
                "SAVINGS" to listOf("저축", "저축보험"),
                "ANNUITY" to listOf("연금", "연금보험"),
                "PROTECTION" to listOf("보장", "보장성"),
                "DRIVER" to listOf("운전자", "운전자보험"),
                "CHILD" to listOf("어린이", "어린이보험", "자녀"),
                "PET" to listOf("펫", "반려동물"),
                "TRAVEL" to listOf("여행", "여행자"),
                "FIRE" to listOf("화재", "화재보험"),
                "AUTO" to listOf("자동차", "자동차보험"),
            )

        return categoryKeywords
            .filter { (_, keywords) -> keywords.any { message.contains(it) } }
            .map { (category, _) -> category }
    }

    /** 내용유형 추출 */
    internal fun extractContentTypes(message: String): List<String> {
        val contentTypeKeywords =
            mapOf(
                "EXEMPTION" to listOf("면책", "면책사항", "면책기간"),
                "REDUCTION" to listOf("감액", "감액기간", "감액지급"),
                "QUALIFICATION" to listOf("가입자격", "가입 자격", "가입조건", "가입 조건"),
                "COVERAGE" to listOf("보장", "보장내용", "보장범위", "보장 내용", "보장 범위", "보험금"),
                "SPECIAL_TERMS" to listOf("특약", "특별약관"),
                "RENEWAL" to listOf("갱신", "갱신형"),
                "CLAIM" to listOf("청구", "보험금 청구", "청구서류"),
                "TERMINATION" to listOf("해지", "해약", "해약환급금"),
            )

        return contentTypeKeywords
            .filter { (_, keywords) -> keywords.any { message.contains(it) } }
            .map { (contentType, _) -> contentType }
    }

    /** 비교 의도 감지 */
    internal fun detectComparison(message: String): Boolean {
        val comparisonKeywords = listOf("비교", "vs", "차이", "차이점", "어디가", "넓은 순서", "좋은 순서", "뭐가 달라")
        return comparisonKeywords.any { message.contains(it) }
    }

    /** 보충 데이터 필요 여부 감지 */
    internal fun detectSupplementaryNeeds(message: String): SupplementaryNeeds =
        SupplementaryNeeds(
            surrenderValue = listOf("해약환급금", "환급", "환급금").any { message.contains(it) },
            premiumExample = listOf("보험료", "월 납입", "납입료", "월납").any { message.contains(it) },
            priceIndex = listOf("가격지수", "지수", "비교지수").any { message.contains(it) },
        )
}
