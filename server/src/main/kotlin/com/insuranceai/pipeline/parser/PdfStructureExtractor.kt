package com.insuranceai.pipeline.parser

import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** LLM 구조화 추출 결과 */
data class PdfStructuredData(
    val exemptions: List<ExtractedItem> = emptyList(),
    val reductions: List<ExtractedItem> = emptyList(),
    val qualifications: List<ExtractedItem> = emptyList(),
    val coverageDetails: List<ExtractedItem> = emptyList(),
    val surrenderValues: List<ExtractedSurrenderValue> = emptyList(),
    val claimDocuments: List<ExtractedItem> = emptyList(),
    val qna: List<ExtractedItem> = emptyList(),
    val overallConfidence: Float = 0f,
)

data class ExtractedItem(
    val content: String,
    val sectionType: String,
    val confidence: Float,
)

data class ExtractedSurrenderValue(
    val elapsedMonths: Int,
    val premiumPaid: Long,
    val surrenderValue: Long,
    val surrenderRate: Float,
    val confidence: Float,
)

/**
 * 추출된 텍스트를 LLM으로 구조화하여 보험 도메인 항목을 추출합니다.
 * 신뢰도 기반으로 자동 승인 / 리뷰 마킹 / 수동 확인으로 분류합니다.
 */
@Component
class PdfStructureExtractor(
    private val suspendingChatClient: SuspendingChatClient,
    private val promptLoader: PromptLoader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 구조 관련 키워드 목록 */
    private val structureKeywords =
        listOf(
            "면책",
            "감액",
            "가입자격",
            "해약환급금",
            "보장내용",
            "청구",
            "서류",
            "보험금 지급",
            "가입나이",
            "계약",
            "특약",
            "주계약",
            "보험기간",
            "납입기간",
        )

    /** 페이지 텍스트에서 구조화된 데이터를 추출 */
    suspend fun extract(
        pages: List<PageExtractionResult>,
        companyName: String,
        productName: String,
    ): PdfStructuredData {
        val relevantPages = filterRelevantPages(pages)
        if (relevantPages.isEmpty()) {
            log.warn("구조화 추출 대상 페이지가 없습니다: {} - {}", companyName, productName)
            return PdfStructuredData()
        }

        val combinedText = relevantPages.joinToString("\n\n---\n\n") { it.text }
        log.debug("구조화 추출 대상 페이지 수: {} / 전체 {}", relevantPages.size, pages.size)

        val prompt =
            promptLoader.render(
                "prompts/pipeline/structure-extraction.st",
                mapOf(
                    "companyName" to companyName,
                    "productName" to productName,
                    "text" to combinedText,
                ),
            )

        val result =
            suspendingChatClient.callEntity(
                userMessage = prompt,
                responseType = PdfStructuredData::class.java,
            )

        val factChecked = factCheck(result, combinedText)
        log.info(
            "구조화 추출 완료: {} - {}, 신뢰도: {}, 팩트체크 통과: {}/{}",
            companyName,
            productName,
            result.overallConfidence,
            factChecked.count { it.foundInOriginal },
            factChecked.size,
        )

        return result
    }

    /** 키워드 기반 관련 페이지 필터링 */
    internal fun filterRelevantPages(pages: List<PageExtractionResult>): List<PageExtractionResult> =
        pages
            .filter { page ->
                structureKeywords.any { keyword -> page.text.contains(keyword) }
            }.ifEmpty { pages }

    /** 추출된 숫자 값이 원본 텍스트에 존재하는지 검증 */
    internal fun factCheck(
        data: PdfStructuredData,
        originalText: String,
    ): List<FactCheckResult> {
        val results = mutableListOf<FactCheckResult>()

        // 해약환급금 숫자 검증
        for (sv in data.surrenderValues) {
            val premiumStr = sv.premiumPaid.toString()
            val valueStr = sv.surrenderValue.toString()

            results.add(
                FactCheckResult(
                    extractedValue = premiumStr,
                    foundInOriginal = findNumberInText(premiumStr, originalText),
                    pattern = "premiumPaid",
                ),
            )
            results.add(
                FactCheckResult(
                    extractedValue = valueStr,
                    foundInOriginal = findNumberInText(valueStr, originalText),
                    pattern = "surrenderValue",
                ),
            )
        }

        return results
    }

    /** 숫자가 원본 텍스트에 존재하는지 확인 (콤마 포함 형태도 검색) */
    private fun findNumberInText(
        number: String,
        text: String,
    ): Boolean {
        if (text.contains(number)) return true
        // 콤마 형식 (예: 1000000 -> 1,000,000)
        val formatted =
            number
                .reversed()
                .chunked(3)
                .joinToString(",")
                .reversed()
        return text.contains(formatted)
    }

    companion object {
        /** 신뢰도 기반 분류 기준 */
        const val AUTO_APPROVE_THRESHOLD = 0.9f
        const val REVIEW_THRESHOLD = 0.7f
    }
}

/** 팩트 체크 결과 */
data class FactCheckResult(
    val extractedValue: String,
    val foundInOriginal: Boolean,
    val pattern: String,
)
