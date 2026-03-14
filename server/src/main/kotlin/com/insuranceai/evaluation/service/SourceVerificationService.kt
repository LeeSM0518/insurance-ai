package com.insuranceai.evaluation.service

import com.insuranceai.chat.dto.SourceInfo
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class SourceVerificationResult(
    val isValid: Boolean,
    val mismatches: List<String>,
)

@Service
class SourceVerificationService(
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 조문번호 패턴: 제N조, 제N조의N
    private val articleNumberPattern = Regex("제(\\d+)조(?:의(\\d+))?")

    /**
     * AI 답변의 인용 출처를 검색 결과와 대조.
     * 비차단(non-blocking) — 불일치 시 경고만 기록.
     */
    suspend fun verify(
        answer: String,
        sources: List<SourceInfo>,
    ): SourceVerificationResult {
        val mismatches = mutableListOf<String>()

        // 1. 답변에서 조문번호 추출
        val citedArticleNumbers = extractArticleNumbers(answer)

        // 2. 답변에서 보험사명 추출
        val citedCompanies = extractCompanyNames(answer)

        // 3. 검색 결과의 보험사명/조문번호와 대조
        val sourceCompanies = sources.map { it.insuranceCompany }.toSet()
        val sourceArticleNumbers = sources.mapNotNull { it.articleNumber }.toSet()

        // 보험사명 불일치 체크
        for (cited in citedCompanies) {
            if (cited !in sourceCompanies) {
                mismatches.add("보험사명 불일치: 답변에 '$cited' 인용, 검색 결과에 없음")
            }
        }

        // 조문번호 불일치 체크
        for (cited in citedArticleNumbers) {
            if (sourceArticleNumbers.none { it.contains(cited) || cited.contains(it) }) {
                mismatches.add("조문번호 불일치: 답변에 '$cited' 인용, 검색 결과에 없음")
            }
        }

        return SourceVerificationResult(
            isValid = mismatches.isEmpty(),
            mismatches = mismatches,
        )
    }

    /** 답변에서 조문번호 추출 */
    fun extractArticleNumbers(text: String): Set<String> = articleNumberPattern.findAll(text).map { it.value }.toSet()

    /** 답변에서 보험사명 추출 */
    suspend fun extractCompanyNames(text: String): Set<String> {
        val companies = insuranceCompanyRepository.findAll().toList()
        val found = mutableSetOf<String>()

        for (company in companies) {
            if (text.contains(company.name)) {
                found.add(company.name)
            }
            // aliases도 체크
            for (alias in company.aliases) {
                if (text.contains(alias)) {
                    found.add(company.name)
                }
            }
        }

        return found
    }
}
