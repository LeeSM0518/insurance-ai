package com.insuranceai.pipeline.parser

import com.insuranceai.pipeline.crawler.CrawlerCommon
import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.dto.SurrenderData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.LocalDate

/**
 * 비교공시 엑셀(HTML) 파일을 파싱하여 ProductRawData 리스트로 변환
 *
 * 6개 카테고리별(PROTECTION/SAVINGS/VARIABLE_PROTECTION/VARIABLE_SAVINGS/PENSION/HEALTH)
 * 공통 로직 + 유형별 분기 처리
 */
@Component
class ComparisonDataParser(
    private val crawlerCommon: CrawlerCommon,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 엑셀(HTML) 파일 파싱 → ProductRawData 리스트
     */
    fun parseFile(
        filePath: Path,
        category: String,
        subcategory: String?,
    ): List<ProductRawData> {
        val mapping =
            ExcelParserConfig.findMapping(filePath.fileName.toString())
                ?: ExcelParserConfig.findMappingByCategory(category, subcategory)

        if (mapping == null) {
            logger.warn("파서 매핑을 찾을 수 없음: $filePath (category=$category)")
            return emptyList()
        }

        return try {
            val table = HtmlExcelParser.parse(filePath)
            logger.info("파일 파싱: ${filePath.fileName} — ${table.headers.size}컬럼, ${table.rows.size}행")

            val sourceUrl = resolveSourceUrl(mapping)

            when (mapping.parserType) {
                ExcelParserConfig.ParserType.PROTECTION -> parseProtection(table, mapping, sourceUrl)
                ExcelParserConfig.ParserType.SAVINGS -> parseSavings(table, mapping, sourceUrl)
                ExcelParserConfig.ParserType.VARIABLE_PROTECTION -> parseVariableProtection(table, mapping, sourceUrl)
                ExcelParserConfig.ParserType.VARIABLE_SAVINGS -> parseVariableSavings(table, mapping, sourceUrl)
                ExcelParserConfig.ParserType.PENSION -> parsePension(table, mapping, sourceUrl)
                ExcelParserConfig.ParserType.HEALTH -> parseHealth(table, mapping, sourceUrl)
            }
        } catch (e: Exception) {
            logger.error("파일 파싱 실패: ${filePath.fileName} — ${e.message}", e)
            emptyList()
        }
    }

    /** 카테고리에 따른 출처 URL */
    private fun resolveSourceUrl(mapping: ExcelParserConfig.FileParserMapping): String =
        if (mapping.category.startsWith("NL_")) {
            "https://kpub.knia.or.kr"
        } else {
            "https://pub.insure.or.kr"
        }

    // ============================================================
    // 보장성 보험 (10파일)
    // ============================================================
    private fun parseProtection(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): List<ProductRawData> {
        val headers = table.headers

        // forward-fill 대상 컬럼 인덱스 찾기
        val companyIdx = HtmlExcelParser.findColumnIndex(headers, "보험회사") ?: 0
        val productIdx = HtmlExcelParser.findColumnIndex(headers, "상품명") ?: 1
        val filledRows = HtmlExcelParser.forwardFill(table.rows, listOf(companyIdx, productIdx))

        // 상품 단위 그룹핑 (보험회사명+상품명)
        val grouped =
            filledRows
                .groupBy { row ->
                    val company = row.getOrElse(companyIdx) { "" }.trim()
                    val product = row.getOrElse(productIdx) { "" }.trim()
                    "$company|$product"
                }.filter { it.key.contains("|") && it.key.split("|").all { p -> p.isNotBlank() } }

        return grouped.mapNotNull { (key, rows) ->
            try {
                val (companyName, productName) = key.split("|", limit = 2)
                buildProtectionProduct(companyName, productName, rows, headers, mapping, sourceUrl)
            } catch (e: Exception) {
                logger.warn("보장성 상품 파싱 실패: $key — ${e.message}")
                null
            }
        }
    }

    private fun buildProtectionProduct(
        companyName: String,
        productName: String,
        rows: List<List<String>>,
        headers: List<String>,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): ProductRawData {
        val firstRow = rows.first()

        // 담보(보장내용) 추출
        val coverages = rows.mapNotNull { row -> extractCoverage(row, headers) }

        // 보험료 추출
        val premiums = extractPremiums(firstRow, headers)

        // 지수 추출
        val indices = extractIndices(firstRow, headers, mapping.subcategory)

        // 공통 필드
        val renewalType = ValueCleaner.cleanRenewalType(findValue(firstRow, headers, "갱신"))
        val salesChannel = findValue(firstRow, headers, "판매채널")
        val salesDate =
            ValueCleaner.cleanDate(findValue(firstRow, headers, "판매일자"))
                ?: ValueCleaner.cleanDate(findValue(firstRow, headers, "판매개시"))
        val notes = findValue(firstRow, headers, "특이사항")
        val phone = ValueCleaner.cleanPhone(findValue(firstRow, headers, "대표번호"))
        val surrenderType = ValueCleaner.cleanSurrenderType(findValue(firstRow, headers, "해약환급금"))

        // 선택적 컬럼: 이율 (종신/정기만)
        val appliedRate =
            if (mapping.subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_INTEREST_RATE) {
                ValueCleaner.cleanDecimal(findValue(firstRow, headers, "확정이율"))
                    ?: ValueCleaner.cleanDecimal(findValue(firstRow, headers, "예정이율"))
            } else {
                null
            }
        val declaredRate =
            if (mapping.subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_INTEREST_RATE) {
                ValueCleaner.cleanDecimal(findValue(firstRow, headers, "공시이율"))
            } else {
                null
            }
        val guaranteedRate =
            if (mapping.subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_INTEREST_RATE) {
                ValueCleaner.cleanDecimal(findValue(firstRow, headers, "최저보증이율"))
            } else {
                null
            }

        val rawContent = rows.joinToString("|") { it.joinToString(",") }
        val checksum = crawlerCommon.calculateChecksum(rawContent.toByteArray())

        return ProductRawData(
            companyName = companyName,
            productName = productName,
            category = mapping.category,
            subcategory = mapping.subcategory,
            renewalType = renewalType,
            salesChannel = salesChannel,
            coverages = coverages,
            premiumExamples = premiums,
            indices = indices,
            checksum = checksum,
            surrenderType = surrenderType,
            productType = findValue(firstRow, headers, "상품특징"),
            salesStartDate = salesDate,
            isUniversal = ValueCleaner.isUniversal(productName),
            notes = notes,
            phone = phone,
            appliedInterestRate = appliedRate,
            declaredInterestRate = declaredRate,
            guaranteedInterestRate = guaranteedRate,
            sourceUrl = sourceUrl,
        )
    }

    /** 담보(보장내용) 추출 — 보장성/변액보장성/실손 공통 */
    private fun extractCoverage(
        row: List<String>,
        headers: List<String>,
    ): CoverageData? {
        val sectionIdx = HtmlExcelParser.findColumnIndex(headers, "구분")
        val coverageIdx =
            HtmlExcelParser.findColumnIndex(headers, "급부명칭")
                ?: HtmlExcelParser.findColumnIndex(headers, "담보명")
                ?: HtmlExcelParser.findColumnIndex(headers, "담보")
        val reasonIdx = HtmlExcelParser.findColumnIndex(headers, "지급사유")
        val amountIdx =
            HtmlExcelParser.findColumnIndex(headers, "지급금액")
                ?: HtmlExcelParser.findColumnIndex(headers, "지급액")

        val coverageName =
            coverageIdx?.let { ValueCleaner.cleanString(row.getOrElse(it) { "" }) }
                ?: return null
        val reason = reasonIdx?.let { ValueCleaner.cleanString(row.getOrElse(it) { "" }) } ?: ""
        val amount = amountIdx?.let { ValueCleaner.cleanString(row.getOrElse(it) { "" }) } ?: ""

        val sectionType =
            sectionIdx?.let { row.getOrElse(it) { "" }.trim() }?.let {
                when {
                    it.contains("주계약") || it.contains("주") -> "MAIN"
                    it.contains("특약") || it.contains("특") -> "RIDER"
                    else -> "MAIN"
                }
            } ?: "MAIN"

        return CoverageData(
            coverageName = coverageName,
            paymentReason = reason,
            paymentAmount = amount,
            sectionType = sectionType,
        )
    }

    /** 보험료 추출 — 남/여 */
    private fun extractPremiums(
        row: List<String>,
        headers: List<String>,
    ): List<PremiumData> {
        val premiums = mutableListOf<PremiumData>()

        val amountIdx = HtmlExcelParser.findColumnIndex(headers, "가입금액")
        val maleIdx =
            HtmlExcelParser.findColumnIndex(headers, "남자")
                ?: HtmlExcelParser.findColumnIndex(headers, "남")
        val femaleIdx =
            HtmlExcelParser.findColumnIndex(headers, "여자")
                ?: HtmlExcelParser.findColumnIndex(headers, "여")

        val insuranceAmount = amountIdx?.let { ValueCleaner.cleanAmount(row.getOrElse(it) { "" }) } ?: 0L

        if (maleIdx != null) {
            val malePremium = ValueCleaner.cleanAmount(row.getOrElse(maleIdx) { "" })
            if (malePremium != null && malePremium > 0) {
                premiums.add(
                    PremiumData(
                        gender = "남",
                        age = 40, // 비교공시 기준 나이 (특이사항에서 추출 가능)
                        insurancePeriod = "",
                        paymentPeriod = "",
                        insuranceAmount = insuranceAmount,
                        premium = malePremium,
                    ),
                )
            }
        }

        if (femaleIdx != null) {
            val femalePremium = ValueCleaner.cleanAmount(row.getOrElse(femaleIdx) { "" })
            if (femalePremium != null && femalePremium > 0) {
                premiums.add(
                    PremiumData(
                        gender = "여",
                        age = 40,
                        insurancePeriod = "",
                        paymentPeriod = "",
                        insuranceAmount = insuranceAmount,
                        premium = femalePremium,
                    ),
                )
            }
        }

        return premiums
    }

    /** 지수 추출 — 보험가격지수, 부가보험료지수, 계약체결비용지수, 보장범위지수 */
    private fun extractIndices(
        row: List<String>,
        headers: List<String>,
        subcategory: String?,
    ): List<IndexData> {
        val indices = mutableListOf<IndexData>()
        val today = LocalDate.now()

        // 보험가격지수
        extractGenderIndex(row, headers, "보험가격지수", "PRICE", today)?.let { indices.addAll(it) }

        // 부가보험료지수
        extractGenderIndex(row, headers, "부가보험료지수", "EXTRA_PREMIUM", today)?.let { indices.addAll(it) }

        // 계약체결비용지수
        extractGenderIndex(row, headers, "계약체결비용지수", "CONTRACT_COST", today)?.let { indices.addAll(it) }

        // 보장범위지수 (암보험만)
        if (subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_COVERAGE_INDEX) {
            extractGenderIndex(row, headers, "보장범위지수", "COVERAGE", today)?.let { indices.addAll(it) }
        }

        return indices
    }

    /** 성별 구분 지수 추출 (남/여 컬럼) */
    private fun extractGenderIndex(
        row: List<String>,
        headers: List<String>,
        indexKeyword: String,
        indexType: String,
        baseDate: LocalDate,
    ): List<IndexData>? {
        val matchingIndices = HtmlExcelParser.findColumnIndices(headers, indexKeyword)
        if (matchingIndices.isEmpty()) return null

        val result = mutableListOf<IndexData>()
        for (idx in matchingIndices) {
            val value = ValueCleaner.cleanDecimal(row.getOrElse(idx) { "" }) ?: continue
            val header = headers[idx]
            val gender =
                when {
                    header.contains("남") -> "남"
                    header.contains("여") -> "여"
                    else -> null
                }
            result.add(IndexData(indexType = indexType, gender = gender, value = value, baseDate = baseDate))
        }
        return result.ifEmpty { null }
    }

    // ============================================================
    // 저축성 보험 (2파일)
    // ============================================================
    private fun parseSavings(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): List<ProductRawData> {
        val headers = table.headers
        val companyIdx = HtmlExcelParser.findColumnIndex(headers, "보험회사") ?: 0
        val productIdx = HtmlExcelParser.findColumnIndex(headers, "상품명") ?: 1
        val filledRows = HtmlExcelParser.forwardFill(table.rows, listOf(companyIdx, productIdx))

        val grouped =
            filledRows
                .groupBy { row ->
                    val company = row.getOrElse(companyIdx) { "" }.trim()
                    val product = row.getOrElse(productIdx) { "" }.trim()
                    "$company|$product"
                }.filter { it.key.split("|").all { p -> p.isNotBlank() } }

        return grouped.mapNotNull { (key, rows) ->
            try {
                val (companyName, productName) = key.split("|", limit = 2)
                buildSavingsProduct(companyName, productName, rows, headers, mapping, sourceUrl)
            } catch (e: Exception) {
                logger.warn("저축성 상품 파싱 실패: $key — ${e.message}")
                null
            }
        }
    }

    private fun buildSavingsProduct(
        companyName: String,
        productName: String,
        rows: List<List<String>>,
        headers: List<String>,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): ProductRawData {
        val firstRow = rows.first()

        // 해약환급금/계약자적립액 추출 (유지기간별)
        val surrenderValues = extractSurrenderValues(firstRow, headers)

        // 이율 정보
        val declaredRate = ValueCleaner.cleanDecimal(findValue(firstRow, headers, "공시이율"))
        val guaranteedRate = ValueCleaner.cleanDecimal(findValue(firstRow, headers, "최저보증이율"))
        val expenseRatio = ValueCleaner.cleanDecimal(findValue(firstRow, headers, "사업비"))

        val rawContent = rows.joinToString("|") { it.joinToString(",") }
        val checksum = crawlerCommon.calculateChecksum(rawContent.toByteArray())

        return ProductRawData(
            companyName = companyName,
            productName = productName,
            category = mapping.category,
            subcategory = mapping.subcategory,
            renewalType = ValueCleaner.cleanRenewalType(findValue(firstRow, headers, "갱신")),
            salesChannel = findValue(firstRow, headers, "판매채널"),
            surrenderValues = surrenderValues,
            checksum = checksum,
            surrenderType = ValueCleaner.cleanSurrenderType(findValue(firstRow, headers, "해약환급금")),
            salesStartDate = ValueCleaner.cleanDate(findValue(firstRow, headers, "판매일자")),
            isUniversal = ValueCleaner.isUniversal(productName),
            notes = findValue(firstRow, headers, "특이사항"),
            phone = ValueCleaner.cleanPhone(findValue(firstRow, headers, "대표번호")),
            declaredInterestRate = declaredRate,
            guaranteedInterestRate = guaranteedRate,
            expenseRatio = expenseRatio,
            sourceUrl = sourceUrl,
        )
    }

    /** 유지기간별 해약환급금/계약자적립액 추출 */
    private fun extractSurrenderValues(
        row: List<String>,
        headers: List<String>,
    ): List<SurrenderData> {
        val result = mutableListOf<SurrenderData>()

        // "해약환급금" 또는 "계약자적립액" 포함 컬럼 찾기
        val surrenderIndices = HtmlExcelParser.findColumnIndices(headers, "해약환급금")
        val reserveIndices = HtmlExcelParser.findColumnIndices(headers, "계약자적립액")

        // 남/여 구분 + 유지기간 추출
        for (idx in surrenderIndices) {
            val header = headers[idx]
            val gender =
                when {
                    header.contains("남") -> "남"
                    header.contains("여") -> "여"
                    else -> null
                }
            // 유지기간은 헤더에서 추출 (예: "3년_남" → 36개월)
            val months = extractMonthsFromHeader(header)
            val value = ValueCleaner.cleanAmount(row.getOrElse(idx) { "" }) ?: continue

            // 대응하는 계약자적립액 찾기
            val reserveValue = findMatchingReserve(reserveIndices, headers, row, gender, months)

            result.add(
                SurrenderData(
                    elapsedMonths = months ?: 0,
                    gender = gender,
                    surrenderValue = value,
                    policyholderReserve = reserveValue,
                ),
            )
        }

        return result
    }

    /** 헤더에서 유지기간(개월 수) 추출 */
    private fun extractMonthsFromHeader(header: String): Int? {
        // "3년" → 36, "6개월" → 6
        val yearMatch = Regex("(\\d+)\\s*년").find(header)
        if (yearMatch != null) return yearMatch.groupValues[1].toInt() * 12

        val monthMatch = Regex("(\\d+)\\s*개월").find(header)
        if (monthMatch != null) return monthMatch.groupValues[1].toInt()

        return null
    }

    /** 대응하는 계약자적립액 값 찾기 */
    private fun findMatchingReserve(
        reserveIndices: List<Int>,
        headers: List<String>,
        row: List<String>,
        gender: String?,
        months: Int?,
    ): Long? {
        for (idx in reserveIndices) {
            val h = headers[idx]
            val rGender =
                when {
                    h.contains("남") -> "남"
                    h.contains("여") -> "여"
                    else -> null
                }
            val rMonths = extractMonthsFromHeader(h)
            if (rGender == gender && rMonths == months) {
                return ValueCleaner.cleanAmount(row.getOrElse(idx) { "" })
            }
        }
        return null
    }

    // ============================================================
    // 변액보장성 보험 (3파일) — PROTECTION 축소판
    // ============================================================
    private fun parseVariableProtection(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): List<ProductRawData> = parseProtection(table, mapping, sourceUrl)

    // ============================================================
    // 변액저축성 보험 (2파일) — SAVINGS 유사 + PDF URL
    // ============================================================
    private fun parseVariableSavings(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): List<ProductRawData> {
        val products = parseSavings(table, mapping, sourceUrl)

        // 변액저축성만 PDF URL이 HTML <a> 태그로 존재할 수 있음
        // (이미 Jsoup 파싱 시 텍스트로 추출됨 — URL은 별도 처리 필요)
        return products
    }

    // ============================================================
    // 연금저축 (1파일) — SAVINGS 유사, 19컬럼
    // ============================================================
    private fun parsePension(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): List<ProductRawData> = parseSavings(table, mapping, sourceUrl)

    // ============================================================
    // 실손의료보험 (1파일) — PROTECTION 유사
    // ============================================================
    private fun parseHealth(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): List<ProductRawData> {
        val headers = table.headers
        val companyIdx = HtmlExcelParser.findColumnIndex(headers, "보험회사") ?: 0
        val productIdx = HtmlExcelParser.findColumnIndex(headers, "상품명") ?: 1
        val filledRows = HtmlExcelParser.forwardFill(table.rows, listOf(companyIdx, productIdx))

        val grouped =
            filledRows
                .groupBy { row ->
                    val company = row.getOrElse(companyIdx) { "" }.trim()
                    val product = row.getOrElse(productIdx) { "" }.trim()
                    "$company|$product"
                }.filter { it.key.split("|").all { p -> p.isNotBlank() } }

        return grouped.mapNotNull { (key, rows) ->
            try {
                val (companyName, productName) = key.split("|", limit = 2)
                buildHealthProduct(companyName, productName, rows, headers, mapping, sourceUrl)
            } catch (e: Exception) {
                logger.warn("실손 상품 파싱 실패: $key — ${e.message}")
                null
            }
        }
    }

    private fun buildHealthProduct(
        companyName: String,
        productName: String,
        rows: List<List<String>>,
        headers: List<String>,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
    ): ProductRawData {
        val firstRow = rows.first()

        // 급부별 담보 추출
        val coverages = rows.mapNotNull { row -> extractCoverage(row, headers) }

        // 급부별 보험료 추출 (남/여)
        val premiums = rows.flatMap { row -> extractPremiums(row, headers) }

        // 지수 추출
        val indices = extractIndices(firstRow, headers, mapping.subcategory)

        val rawContent = rows.joinToString("|") { it.joinToString(",") }
        val checksum = crawlerCommon.calculateChecksum(rawContent.toByteArray())

        return ProductRawData(
            companyName = companyName,
            productName = productName,
            category = mapping.category,
            subcategory = mapping.subcategory,
            renewalType = ValueCleaner.cleanRenewalType(findValue(firstRow, headers, "갱신")),
            salesChannel = findValue(firstRow, headers, "판매채널"),
            coverages = coverages,
            premiumExamples = premiums,
            indices = indices,
            checksum = checksum,
            salesStartDate = ValueCleaner.cleanDate(findValue(firstRow, headers, "판매일자")),
            notes = findValue(firstRow, headers, "특이사항"),
            phone = ValueCleaner.cleanPhone(findValue(firstRow, headers, "대표번호")),
            sourceUrl = sourceUrl,
        )
    }

    // ============================================================
    // 공통 헬퍼
    // ============================================================

    /** 헤더 키워드로 값 찾기 */
    private fun findValue(
        row: List<String>,
        headers: List<String>,
        keyword: String,
    ): String? {
        val idx = HtmlExcelParser.findColumnIndex(headers, keyword) ?: return null
        return ValueCleaner.cleanString(row.getOrElse(idx) { "" })
    }
}
