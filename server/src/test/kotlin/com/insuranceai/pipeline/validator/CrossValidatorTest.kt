package com.insuranceai.pipeline.validator

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.parser.ExtractedItem
import com.insuranceai.pipeline.parser.PdfStructuredData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossValidatorTest {
    private val validator = CrossValidator()

    private fun coverage(
        name: String,
        reason: String = "",
    ) = CoverageData(
        coverageName = name,
        paymentReason = reason,
        paymentAmount = "1000만원",
        sectionType = "MAIN",
    )

    private fun pdfData(coverageContents: List<String>) =
        PdfStructuredData(
            coverageDetails =
                coverageContents.map {
                    ExtractedItem(content = it, sectionType = "MAIN", confidence = 0.9f)
                },
        )

    @Nested
    inner class Validate {
        @Test
        fun `정확 일치 시 matched 증가`() {
            val coverages = listOf(coverage("사망보험금"))
            val pdf = listOf(pdfData(listOf("사망보험금")))

            val result = validator.validate(coverages, pdf, "테스트상품", "테스트보험사")

            assertEquals(1, result.matched)
            assertEquals(0, result.missing)
        }

        @Test
        fun `PDF에서 누락된 담보 감지`() {
            val coverages = listOf(coverage("사망보험금"), coverage("입원비"))
            val pdf = listOf(pdfData(emptyList()))

            val result = validator.validate(coverages, pdf, "테스트상품", "테스트보험사")

            assertEquals(2, result.missing)
            assertEquals(2, result.warnings.size)
        }

        @Test
        fun `형식 불일치 자동 해소 (포함 관계로 높은 유사도)`() {
            val coverages = listOf(coverage("사망보험금"))
            val pdf = listOf(pdfData(listOf("사망보험금 지급 안내")))

            val result = validator.validate(coverages, pdf, "테스트상품", "테스트보험사")

            // 포함 관계 -> 유사도 0.9 -> formatMismatch
            assertTrue(result.matched + result.formatMismatch >= 1)
        }

        @Test
        fun `값 불일치 감지`() {
            val coverages = listOf(coverage("사망보험금"))
            val pdf = listOf(pdfData(listOf("완전히 다른 내용의 보장")))

            val result = validator.validate(coverages, pdf, "테스트상품", "테스트보험사")

            assertTrue(result.valueMismatch > 0 || result.partialMismatch > 0)
        }

        @Test
        fun `지급사유 키워드 검증`() {
            val coverages = listOf(coverage("사망보험금", "피보험자가 사망한 경우"))
            val pdf = listOf(pdfData(listOf("사망보험금: 피보험자가 사망한 경우 지급")))

            val result = validator.validate(coverages, pdf, "테스트상품", "테스트보험사")

            // 키워드 "사망" 포함되므로 경고 없음
            val keywordWarnings = result.warnings.filter { it.contains("키워드 미포함") }
            assertEquals(0, keywordWarnings.size)
        }
    }

    @Nested
    inner class LevenshteinDistance {
        @Test
        fun `동일 문자열이면 거리 0`() {
            assertEquals(0, validator.levenshteinDistance("abc", "abc"))
        }

        @Test
        fun `완전히 다른 문자열`() {
            val distance = validator.levenshteinDistance("abc", "xyz")
            assertEquals(3, distance)
        }

        @Test
        fun `한 글자 차이`() {
            assertEquals(1, validator.levenshteinDistance("abc", "ab"))
        }
    }

    @Nested
    inner class CalculateSimilarity {
        @Test
        fun `동일 문자열이면 유사도 1`() {
            assertEquals(1.0, validator.calculateSimilarity("사망보험금", "사망보험금"))
        }

        @Test
        fun `포함 관계면 유사도 0_9`() {
            assertEquals(0.9, validator.calculateSimilarity("사망", "사망보험금"))
        }

        @Test
        fun `빈 문자열이면 유사도 0`() {
            assertEquals(0.0, validator.calculateSimilarity("", "테스트"))
        }
    }
}
