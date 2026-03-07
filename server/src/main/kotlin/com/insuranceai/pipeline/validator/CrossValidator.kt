package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.parser.PdfStructuredData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
class CrossValidator {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val SIMILARITY_THRESHOLD = 0.8
    }

    data class CrossValidationResult(
        val matched: Int,
        val formatMismatch: Int,
        val partialMismatch: Int,
        val valueMismatch: Int,
        val missing: Int,
        val warnings: List<String>,
    )

    /** 비교공시 보장내용 vs PDF COVERAGE_DETAIL 교차 대조 */
    fun validate(
        associationCoverages: List<CoverageData>,
        pdfArticles: List<PdfStructuredData>,
        productName: String,
        companyName: String,
    ): CrossValidationResult {
        val warnings = mutableListOf<String>()
        var matched = 0
        var formatMismatch = 0
        var partialMismatch = 0
        var valueMismatch = 0
        var missing = 0

        // PDF에서 추출한 보장 상세 내용을 평탄화
        val pdfCoverageContents =
            pdfArticles
                .flatMap { it.coverageDetails }
                .map { it.content }

        for (coverage in associationCoverages) {
            val coverageName = coverage.coverageName.trim()

            // 퍼지 매칭: PDF 보장 내용 중 유사한 항목 찾기
            val bestMatch =
                pdfCoverageContents.maxByOrNull { pdfContent ->
                    calculateSimilarity(coverageName, pdfContent)
                }

            if (bestMatch == null) {
                missing++
                warnings.add("[$companyName] $productName: 비교공시 담보 '$coverageName' PDF에서 누락")
                continue
            }

            val similarity = calculateSimilarity(coverageName, bestMatch)

            when {
                similarity >= 1.0 -> matched++
                similarity >= SIMILARITY_THRESHOLD -> {
                    // 형식 차이로 인한 불일치 (정규화 후 해소 가능)
                    formatMismatch++
                }
                similarity >= 0.5 -> {
                    // PDF가 상위집합일 수 있음 (부분 일치)
                    partialMismatch++
                }
                else -> {
                    valueMismatch++
                    warnings.add("[$companyName] $productName: 담보 '$coverageName' 값 불일치 (유사도: ${"%.2f".format(similarity)})")
                }
            }

            // 지급사유 키워드 포함 여부 검증
            if (coverage.paymentReason.isNotBlank()) {
                val keywords = extractKeywords(coverage.paymentReason)
                val keywordFound = keywords.any { keyword -> bestMatch.contains(keyword) }
                if (!keywordFound && keywords.isNotEmpty()) {
                    warnings.add("[$companyName] $productName: 담보 '$coverageName' 지급사유 키워드 미포함")
                }
            }
        }

        val result =
            CrossValidationResult(
                matched = matched,
                formatMismatch = formatMismatch,
                partialMismatch = partialMismatch,
                valueMismatch = valueMismatch,
                missing = missing,
                warnings = warnings,
            )

        if (warnings.isNotEmpty()) {
            logger.warn(
                "크로스 검증 결과 [$companyName/$productName]: " +
                    "일치=${result.matched}, 형식불일치=${result.formatMismatch}, " +
                    "부분불일치=${result.partialMismatch}, 값불일치=${result.valueMismatch}, " +
                    "누락=${result.missing}",
            )
            warnings.forEach { logger.warn(it) }
        }

        return result
    }

    /** 두 문자열의 유사도 계산 (Levenshtein 기반) */
    internal fun calculateSimilarity(
        s1: String,
        s2: String,
    ): Double {
        val normalized1 = normalizeForComparison(s1)
        val normalized2 = normalizeForComparison(s2)

        if (normalized1 == normalized2) return 1.0
        if (normalized1.isEmpty() || normalized2.isEmpty()) return 0.0

        // 짧은 문자열이 긴 문자열에 포함되면 높은 유사도
        if (normalized2.contains(normalized1)) return 0.9
        if (normalized1.contains(normalized2)) return 0.9

        val distance = levenshteinDistance(normalized1, normalized2)
        val maxLen = maxOf(normalized1.length, normalized2.length)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /** Levenshtein 편집 거리 계산 */
    internal fun levenshteinDistance(
        s1: String,
        s2: String,
    ): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] =
                    min(
                        min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost,
                    )
            }
        }

        return dp[m][n]
    }

    /** 비교용 문자열 정규화 */
    private fun normalizeForComparison(text: String): String =
        text
            .trim()
            .replace("\\s+".toRegex(), " ")
            .lowercase()

    /** 지급사유에서 핵심 키워드 추출 */
    private fun extractKeywords(paymentReason: String): List<String> =
        paymentReason
            .replace("[()（）\\[\\]「」]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.length >= 2 }
            .take(5)
}
