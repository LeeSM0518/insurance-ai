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

    /** 금액 필드에서 참조 텍스트(실손의료보험 '용어해설참조' 등)를 Empty 처리하는 표현 */
    private val REFERENCE_EXPRESSIONS = setOf("용어해설참조", "용어해설 참조", "별도참조", "별도 참조")
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

    // --- 새 메서드: ParseResult 반환 ---

    /** 금액 파싱: ParseResult 반환 */
    fun parseAmount(value: String?): ParseResult<Long> {
        if (value == null) return ParseResult.Empty
        val trimmed = value.trim()
        if (trimmed in NULL_EXPRESSIONS) return ParseResult.Empty

        // 억원 단위
        val eokMatch = Regex("([\\d,.]+)\\s*억\\s*원?").find(trimmed)
        if (eokMatch != null) {
            val num =
                eokMatch.groupValues[1].replace(",", "").toBigDecimalOrNull()
                    ?: return ParseResult.Failure(trimmed, "억원 단위 숫자 변환 실패")
            return ParseResult.Success((num * BigDecimal(100_000_000)).toLong())
        }

        // 만원 단위
        val manMatch = Regex("([\\d,.]+)\\s*만\\s*원?").find(trimmed)
        if (manMatch != null) {
            val num =
                manMatch.groupValues[1].replace(",", "").toBigDecimalOrNull()
                    ?: return ParseResult.Failure(trimmed, "만원 단위 숫자 변환 실패")
            return ParseResult.Success((num * BigDecimal(10_000)).toLong())
        }

        // 참조 텍스트(실손의료보험 '용어해설참조' 등) → 데이터 없음
        if (trimmed in REFERENCE_EXPRESSIONS) return ParseResult.Empty

        // 일반 금액 (콤마, 공백, '원' 제거)
        val cleaned =
            trimmed
                .replace(WON_SUFFIX_REGEX, "")
                .replace(COMMA_REGEX, "")
                .trim()
        return cleaned
            .toLongOrNull()
            ?.let { ParseResult.Success(it) }
            ?: ParseResult.Failure(trimmed, "금액 파싱 실패")
    }

    /** 금액 클렌징: "836000  원", "806,000  원", "10,000만원", "1억원" → Long */
    fun cleanAmount(value: String?): Long? = parseAmount(value).getOrNull()

    /** 괄호 앞 숫자 추출 정규식: "1.90(0.3)" → "1.90" */
    private val PARENTHESIS_PREFIX_REGEX = Regex("^(\\d+\\.?\\d*)\\s*\\(")

    /** 복합 이율 텍스트 패턴: 여러 이율이 나열된 텍스트 → parseDecimal에서는 Empty 처리 */
    private val COMPOUND_RATE_REGEX = Regex("\\d+\\.\\d+%.*\\d+\\.\\d+%")

    /** 소수 파싱: ParseResult 반환 */
    fun parseDecimal(value: String?): ParseResult<BigDecimal> {
        if (value == null) return ParseResult.Empty
        val trimmed = value.trim()
        if (trimmed in NULL_EXPRESSIONS) return ParseResult.Empty
        // 복합 이율 텍스트는 단일 소수로 변환 불가 → Empty (parseGuaranteedRate에서 처리)
        if (COMPOUND_RATE_REGEX.containsMatchIn(trimmed)) return ParseResult.Empty

        var cleaned =
            trimmed
                .replace(PERCENT_SUFFIX_REGEX, "")
                .replace(COMMA_REGEX, "")
                .trim()
        // '%'만 있던 경우 클렌징 후 빈 문자열 → 데이터 없음
        if (cleaned.isEmpty()) return ParseResult.Empty
        // 괄호가 있으면 괄호 앞 숫자만 추출: "1.90(0.3)" → "1.90"
        val parenMatch = PARENTHESIS_PREFIX_REGEX.find(cleaned)
        if (parenMatch != null) {
            cleaned = parenMatch.groupValues[1]
        }
        return cleaned
            .toBigDecimalOrNull()
            ?.let { ParseResult.Success(it) }
            ?: ParseResult.Failure(trimmed, "소수 파싱 실패")
    }

    /** 이율/지수 클렌징: "2.70 %" → BigDecimal(2.70), "1.90(0.3)" → BigDecimal(1.90) */
    fun cleanDecimal(value: String?): BigDecimal? = parseDecimal(value).getOrNull()

    /** 이율 값 추출 정규식 */
    private val RATE_REGEX = Regex("(\\d+\\.\\d+)")

    /**
     * 최저보증이율 추출: 단일 이율만 숫자 반환, 복합 이율(2개 이상)은 null 반환
     *
     * - 이율 값 1개 → 해당 값 반환
     * - 이율 값 2개 이상 (괄호, 기간별, 복합) → null (원본 텍스트 보존은 호출자 책임)
     */
    fun parseGuaranteedRate(value: String?): BigDecimal? {
        if (isNull(value)) return null
        val trimmed = value!!.trim()

        // 모든 소수점 숫자 추출
        val allRates = RATE_REGEX.findAll(trimmed).map { it.groupValues[1].toBigDecimal() }.toList()

        // 이율 값이 정확히 1개 → 숫자 저장
        if (allRates.size == 1) return allRates[0]

        // 이율 값이 2개 이상 → null (복합 이율)
        return null
    }

    /** 복합 이율 여부 판정: 이율 값이 2개 이상이면 true */
    fun isCompoundRate(value: String?): Boolean {
        if (isNull(value)) return false
        val trimmed = value!!.trim()
        val rateCount = RATE_REGEX.findAll(trimmed).count()
        return rateCount >= 2
    }

    /** 단순 괄호 패턴 정규식: "1.90(0.3)" */
    private val SIMPLE_PAREN_REGEX = Regex("^(\\d+\\.\\d+)\\s*\\((\\d+\\.?\\d*)\\)$")

    /**
     * 최저보증이율 원본 텍스트 포맷: 복합 이율일 때만 호출
     *
     * - 단순 괄호 "1.90(0.3)" → "공시이율 1.90%, 최저보증이율 0.3%"
     * - 나머지 (기간별, 복합 텍스트) → 원본 그대로 반환
     */
    fun formatGuaranteedRateText(value: String?): String? {
        if (isNull(value)) return null
        val trimmed = value!!.trim()

        val simpleMatch = SIMPLE_PAREN_REGEX.find(trimmed)
        if (simpleMatch != null) {
            val declared = simpleMatch.groupValues[1]
            val guaranteed = simpleMatch.groupValues[2]
            return "공시이율 $declared%, 최저보증이율 $guaranteed%"
        }

        return trimmed
    }

    /** 유효 기간 패턴 (LLM 결과 후속 검증용) */
    private val VALID_PERIOD_PATTERN =
        Regex("^(\\d+년납?|\\d+세만기?|종신|전기납|일시납|\\d+년만기|\\d+개월)$")

    /** 기간 값 검증: 유효 패턴이면 반환, 아니면 null */
    fun cleanPeriod(value: String?): String? {
        val cleaned = cleanString(value) ?: return null
        return if (VALID_PERIOD_PATTERN.matches(cleaned)) cleaned else null
    }

    /** 정수 파싱: ParseResult 반환 */
    fun parseInt(value: String?): ParseResult<Int> {
        if (value == null) return ParseResult.Empty
        val trimmed = value.trim()
        if (trimmed in NULL_EXPRESSIONS) return ParseResult.Empty

        val cleaned = trimmed.replace(NON_NUMERIC_REGEX, "")
        return cleaned
            .toIntOrNull()
            ?.let { ParseResult.Success(it) }
            ?: ParseResult.Failure(trimmed, "정수 파싱 실패")
    }

    /** 정수 클렌징 */
    fun cleanInt(value: String?): Int? = parseInt(value).getOrNull()

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

    /** 날짜 파싱: ParseResult 반환 */
    fun parseDate(value: String?): ParseResult<LocalDate> {
        if (value == null) return ParseResult.Empty
        val trimmed = value.trim()
        if (trimmed in NULL_EXPRESSIONS) return ParseResult.Empty

        for (fmt in DATE_FORMATTERS) {
            try {
                return ParseResult.Success(LocalDate.parse(trimmed, fmt))
            } catch (_: DateTimeParseException) {
                // 다음 포맷 시도
            }
        }
        return ParseResult.Failure(trimmed, "날짜 포맷 매칭 실패 (시도: ${DATE_FORMATTERS.size}개)")
    }

    /** 판매일자 클렌징 */
    fun cleanDate(value: String?): LocalDate? = parseDate(value).getOrNull()

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
