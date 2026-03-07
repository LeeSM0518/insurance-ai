package com.insuranceai.pipeline.parser

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 비교공시 엑셀(HTML) 데이터 값 클렌징 유틸리티
 */
object ValueCleaner {
    private val NULL_EXPRESSIONS = setOf("-", "해당없음", "NaN", "nan", "N/A", "n/a", "", " ")
    private val COMMA_REGEX = Regex("[,\\s]")
    private val WON_SUFFIX_REGEX = Regex("\\s*원\\s*$")
    private val PERCENT_SUFFIX_REGEX = Regex("\\s*%\\s*$")
    private val NON_NUMERIC_REGEX = Regex("[^\\d.\\-]")
    private val DATE_FORMATTERS =
        listOf(
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
        )

    /** null 표현 통합 처리 */
    fun isNull(value: String?): Boolean {
        if (value == null) return true
        return value.trim() in NULL_EXPRESSIONS
    }

    /** 문자열 클렌징 (null이면 null 반환) */
    fun cleanString(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        return if (trimmed in NULL_EXPRESSIONS) null else trimmed
    }

    /** 금액 클렌징: "836000  원", "806,000  원", "10,000만원", "1억원" → Long */
    fun cleanAmount(value: String?): Long? {
        if (isNull(value)) return null
        val trimmed = value!!.trim()

        // 억원 단위
        val eokMatch = Regex("([\\d,.]+)\\s*억\\s*원?").find(trimmed)
        if (eokMatch != null) {
            val num = eokMatch.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
            return (num * BigDecimal(100_000_000)).toLong()
        }

        // 만원 단위
        val manMatch = Regex("([\\d,.]+)\\s*만\\s*원?").find(trimmed)
        if (manMatch != null) {
            val num = manMatch.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
            return (num * BigDecimal(10_000)).toLong()
        }

        // 일반 금액 (콤마, 공백, '원' 제거)
        val cleaned =
            trimmed
                .replace(WON_SUFFIX_REGEX, "")
                .replace(COMMA_REGEX, "")
                .trim()
        return cleaned.toLongOrNull()
    }

    /** 이율/지수 클렌징: "2.70 %" → BigDecimal(2.70) */
    fun cleanDecimal(value: String?): BigDecimal? {
        if (isNull(value)) return null
        val cleaned =
            value!!
                .trim()
                .replace(PERCENT_SUFFIX_REGEX, "")
                .replace(COMMA_REGEX, "")
                .trim()
        return cleaned.toBigDecimalOrNull()
    }

    /** 정수 클렌징 */
    fun cleanInt(value: String?): Int? {
        if (isNull(value)) return null
        val cleaned = value!!.trim().replace(NON_NUMERIC_REGEX, "")
        return cleaned.toIntOrNull()
    }

    /** 갱신유형 정규화 */
    fun cleanRenewalType(value: String?): String? {
        if (isNull(value)) return null
        return when (val trimmed = value!!.trim()) {
            "갱신형", "갱신" -> "RENEWAL"
            "비갱신형", "비갱신" -> "NON_RENEWAL"
            else -> trimmed
        }
    }

    /** 유니버셜 여부 판별 */
    fun isUniversal(productName: String): Boolean =
        productName.contains("유니버셜", ignoreCase = true) || productName.contains("UL", ignoreCase = false)

    /** 판매일자 클렌징 */
    fun cleanDate(value: String?): LocalDate? {
        if (isNull(value)) return null
        val trimmed = value!!.trim()
        for (fmt in DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, fmt)
            } catch (_: DateTimeParseException) {
                // 다음 포맷 시도
            }
        }
        return null
    }

    /** 전화번호 정규화: "02-1588-1001", "1588-1001" 등 */
    fun cleanPhone(value: String?): String? {
        if (isNull(value)) return null
        return value!!.trim().replace(Regex("[^\\d\\-]"), "")
    }

    /** 유지기간 → 경과 개월 수 변환: "1년" → 12, "6개월" → 6, "0.25" → 3 */
    fun parseElapsedMonths(value: String?): Int? {
        if (isNull(value)) return null
        val trimmed = value!!.trim()

        // "N년" 패턴
        val yearMatch = Regex("(\\d+)\\s*년").find(trimmed)
        if (yearMatch != null) return yearMatch.groupValues[1].toInt() * 12

        // "N개월" 패턴
        val monthMatch = Regex("(\\d+)\\s*개월").find(trimmed)
        if (monthMatch != null) return monthMatch.groupValues[1].toInt()

        // 소수점 (년 단위 소수): "0.25" → 3개월
        val decimal = trimmed.toDoubleOrNull()
        if (decimal != null) return (decimal * 12).toInt()

        return trimmed.toIntOrNull()
    }

    /** 해약환급금 유형 정규화 */
    fun cleanSurrenderType(value: String?): String? {
        if (isNull(value)) return null
        val trimmed = value!!.trim()
        return when {
            trimmed.contains("무해지") || trimmed.contains("무·저해지") -> "무해지저해지"
            trimmed.contains("저해지") -> "무해지저해지"
            trimmed.contains("순수보장") -> "순수보장"
            trimmed.contains("만기환급") -> "만기환급"
            trimmed.contains("일반") || trimmed.contains("표준") -> "일반"
            else -> trimmed
        }
    }

    /** 성별 정규화 */
    fun cleanGender(value: String?): String? {
        if (isNull(value)) return null
        return when (val trimmed = value!!.trim()) {
            "남", "남자", "M", "m", "male" -> "남"
            "여", "여자", "F", "f", "female" -> "여"
            else -> trimmed
        }
    }
}
