package com.insuranceai.pipeline.parser

import com.insuranceai.pipeline.crawler.CrawlerCommon
import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.FeatureData
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
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val mapping =
            ExcelParserConfig.findMapping(filePath.fileName.toString())
                ?: ExcelParserConfig.findMappingByCategory(category, subcategory)

        if (mapping == null) {
            logger.warn("파서 매핑을 찾을 수 없음: $filePath (category=$category)")
            return emptyList()
        }

        return try {
            val table = parseExcelFile(filePath)
            logger.info("파일 파싱: ${filePath.fileName} — ${table.headers.size}컬럼, ${table.rows.size}행")

            val sourceUrl = resolveSourceUrl(mapping)

            val products =
                when (mapping.parserType) {
                    ExcelParserConfig.ParserType.PROTECTION -> parseProtection(table, mapping, sourceUrl, warningCollector)
                    ExcelParserConfig.ParserType.SAVINGS -> parseSavings(table, mapping, sourceUrl, warningCollector)
                    ExcelParserConfig.ParserType.VARIABLE_PROTECTION -> parseVariableProtection(table, mapping, sourceUrl, warningCollector)
                    ExcelParserConfig.ParserType.VARIABLE_SAVINGS -> parseVariableSavings(table, mapping, sourceUrl, warningCollector)
                    ExcelParserConfig.ParserType.PENSION -> parsePension(table, mapping, sourceUrl, warningCollector)
                    ExcelParserConfig.ParserType.HEALTH -> parseHealth(table, mapping, sourceUrl, warningCollector)
                }

            // 파일 단위 집계 로깅 (WARN)
            if (warningCollector.failureCount > 0) {
                logger.warn("파싱 경고 [{}]: {}", filePath.fileName, warningCollector.summary())
            }

            products
        } catch (e: Exception) {
            logger.error("파일 파싱 실패: ${filePath.fileName} — ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 파일 형식 자동 감지: 바이너리 Excel(손보) vs HTML 테이블(생보)
     *
     * 파일 앞 8바이트의 매직 바이트로 판별:
     * - D0 CF 11 E0 = OLE2 Compound Document (바이너리 .xls) → BinaryExcelParser
     * - 그 외 (HTML 텍스트) → HtmlExcelParser
     */
    private fun parseExcelFile(filePath: Path): ParsedTable {
        val header = filePath.toFile().inputStream().use { it.readNBytes(8) }
        val isOle2 =
            header.size >= 4 &&
                header[0] == 0xD0.toByte() &&
                header[1] == 0xCF.toByte() &&
                header[2] == 0x11.toByte() &&
                header[3] == 0xE0.toByte()

        return if (isOle2) {
            logger.debug("바이너리 Excel 감지: ${filePath.fileName}")
            BinaryExcelParser.parse(filePath)
        } else {
            logger.debug("HTML Excel 감지: ${filePath.fileName}")
            HtmlExcelParser.parse(filePath)
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val headers = table.headers

        // forward-fill 대상 컬럼 인덱스 찾기
        val companyIdx =
            HtmlExcelParser.findColumnIndex(headers, "보험회사")
                ?: HtmlExcelParser.findColumnIndex(headers, "회사명")
                ?: 0
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
                buildProtectionProduct(companyName, productName, rows, headers, mapping, sourceUrl, collector)
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): ProductRawData {
        val firstRow = rows.first()
        val context = "$companyName|$productName"

        // 담보(보장내용) 추출
        val coverages = rows.mapNotNull { row -> extractCoverage(row, headers) }

        // 공통 필드 — 갱신여부: "갱신여부" 우선 → "갱신주기" → "갱신" fallback
        val renewalType =
            ValueCleaner.cleanRenewalType(
                findValue(firstRow, headers, "갱신여부")
                    ?: findValue(firstRow, headers, "갱신주기")
                    ?: findValue(firstRow, headers, "갱신"),
            )
        // 채널: "판매채널" → "채널" fallback
        val salesChannel =
            findValue(firstRow, headers, "판매채널")
                ?: findValue(firstRow, headers, "채널")
        val salesDate =
            collector.collectOrNull(
                ValueCleaner.parseDate(findValue(firstRow, headers, "판매일자")),
                field = "salesStartDate",
                context = context,
            ) ?: collector.collectOrNull(
                ValueCleaner.parseDate(findValue(firstRow, headers, "판매개시")),
                field = "salesStartDate",
                context = context,
            )
        val notes = findValue(firstRow, headers, "특이사항")
        val phone = ValueCleaner.cleanPhone(findValue(firstRow, headers, "대표번호"))
        val surrenderType = ValueCleaner.cleanSurrenderType(findValue(firstRow, headers, "해약환급금"))

        // 보험료 추출 (특이사항에서 비교 기준 조건 추출)
        val premiums = extractPremiums(firstRow, headers, notes, mapping.subcategory, collector, context)

        // 지수 추출
        val indices = extractIndices(firstRow, headers, mapping.subcategory, rows, collector, context)

        // 선택적 컬럼: 이율 (생보 종신/정기 + 손보 보장성 PB* 전체)
        val hasInterestRate =
            mapping.subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_INTEREST_RATE ||
                mapping.subcategory?.startsWith("PB") == true
        val appliedRate =
            if (hasInterestRate) {
                collector.collectOrNull(
                    ValueCleaner.parseDecimal(findValue(firstRow, headers, "확정이율")),
                    field = "appliedInterestRate",
                    context = context,
                ) ?: collector.collectOrNull(
                    ValueCleaner.parseDecimal(findValue(firstRow, headers, "예정이율")),
                    field = "appliedInterestRate",
                    context = context,
                )
            } else {
                null
            }
        val declaredRate =
            if (hasInterestRate) {
                collector.collectOrNull(
                    ValueCleaner.parseDecimal(findValue(firstRow, headers, "공시이율")),
                    field = "declaredInterestRate",
                    context = context,
                )
            } else {
                null
            }
        val guaranteedRate =
            if (hasInterestRate) {
                ValueCleaner.parseGuaranteedRate(findValue(firstRow, headers, "최저보증이율"))
                    ?: collector.collectOrNull(
                        ValueCleaner.parseDecimal(findValue(firstRow, headers, "최저보증이율")),
                        field = "guaranteedInterestRate",
                        context = context,
                    )
            } else {
                null
            }

        // 선택적 컬럼: 경도치매 (PB16)
        val features = mutableListOf<FeatureData>()
        if (mapping.subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_MILD_DEMENTIA) {
            val mildDementia =
                findValue(firstRow, headers, "경도치매보장여부")
                    ?: findValue(firstRow, headers, "경도치매")
            if (mildDementia != null) {
                features.add(FeatureData("MILD_DEMENTIA_COVERED", mildDementia))
            }
            val dementiaBenefit = findValue(firstRow, headers, "진단급여금")
            if (dementiaBenefit != null) {
                features.add(FeatureData("MILD_DEMENTIA_BENEFIT", dementiaBenefit))
            }
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
            features = features,
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

    /** 보험료 추출 — 남/여, 특이사항에서 비교 기준 조건 추출 */
    private fun extractPremiums(
        row: List<String>,
        headers: List<String>,
        notes: String? = null,
        subcategory: String? = null,
        collector: ParsingWarningCollector = ParsingWarningCollector(),
        context: String = "",
    ): List<PremiumData> {
        val premiums = mutableListOf<PremiumData>()

        val amountIdx = HtmlExcelParser.findColumnIndex(headers, "가입금액")
        val maleIdx =
            HtmlExcelParser.findColumnIndex(headers, "남자")
                ?: HtmlExcelParser.findColumnIndex(headers, "남")
        val femaleIdx =
            HtmlExcelParser.findColumnIndex(headers, "여자")
                ?: HtmlExcelParser.findColumnIndex(headers, "여")

        val insuranceAmount =
            amountIdx?.let {
                val raw = row.getOrElse(it) { "" }
                collector.collectOrNull(
                    ValueCleaner.parseAmount(raw),
                    field = "insuranceAmount",
                    context = context,
                ) ?: 0L
            } ?: 0L

        // 특이사항에서 보험료 비교 기준 조건 추출
        val conditions =
            try {
                extractPremiumConditions(notes, subcategory)
            } catch (e: ParsingException) {
                logger.warn("보험료 기준 조건 추출 실패: ${e.message}")
                return emptyList()
            }

        if (maleIdx != null) {
            val malePremium = ValueCleaner.cleanAmount(row.getOrElse(maleIdx) { "" })
            if (malePremium != null && malePremium > 0) {
                premiums.add(
                    PremiumData(
                        gender = "남",
                        age = conditions.age,
                        insurancePeriod = conditions.insurancePeriod,
                        paymentPeriod = conditions.paymentPeriod,
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
                        age = conditions.age,
                        insurancePeriod = conditions.insurancePeriod,
                        paymentPeriod = conditions.paymentPeriod,
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
        allRows: List<List<String>> = listOf(row),
        collector: ParsingWarningCollector = ParsingWarningCollector(),
        context: String = "",
    ): List<IndexData> {
        val indices = mutableListOf<IndexData>()
        val today = LocalDate.now()

        // 보험가격지수
        extractGenderIndex(row, headers, "보험가격지수", "PRICE", today, collector, context)?.let { indices.addAll(it) }

        // 부가보험료지수
        extractGenderIndex(row, headers, "부가보험료지수", "EXTRA_PREMIUM", today, collector, context)?.let { indices.addAll(it) }

        // 계약체결비용지수
        extractGenderIndex(row, headers, "계약체결비용지수", "CONTRACT_COST", today, collector, context)?.let { indices.addAll(it) }

        // 보장범위지수 (암보험 — PB22는 다중 행에 걸쳐 있을 수 있음)
        if (subcategory in ExcelParserConfig.ProtectionOptionalColumns.HAS_COVERAGE_INDEX) {
            for (r in allRows) {
                extractGenderIndex(r, headers, "보장범위지수", "COVERAGE", today, collector, context)?.let { indices.addAll(it) }
            }
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
        context: String = "",
    ): List<IndexData>? {
        val matchingIndices = HtmlExcelParser.findColumnIndices(headers, indexKeyword)
        if (matchingIndices.isEmpty()) return null

        val result = mutableListOf<IndexData>()
        for (idx in matchingIndices) {
            val value =
                collector.collectOrNull(
                    ValueCleaner.parseDecimal(row.getOrElse(idx) { "" }),
                    field = indexType,
                    context = context,
                ) ?: continue
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val headers = table.headers
        val companyIdx =
            HtmlExcelParser.findColumnIndex(headers, "보험회사")
                ?: HtmlExcelParser.findColumnIndex(headers, "회사명")
                ?: 0
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
                buildSavingsProduct(companyName, productName, rows, headers, mapping, sourceUrl, collector)
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): ProductRawData {
        val firstRow = rows.first()
        val context = "$companyName|$productName"

        // 해약환급금/계약자적립액 추출 (유지기간별)
        // 손보 저축성/연금: 행(세로) 방향 유지기간 → rows 전체 순회
        val surrenderValues = extractSurrenderValuesWithRows(rows, headers)

        // 이율 정보
        val declaredRate =
            collector.collectOrNull(
                ValueCleaner.parseDecimal(findValue(firstRow, headers, "공시이율")),
                field = "declaredInterestRate",
                context = context,
            )
        val guaranteedRate =
            ValueCleaner.parseGuaranteedRate(findValue(firstRow, headers, "최저보증이율"))
                ?: collector.collectOrNull(
                    ValueCleaner.parseDecimal(findValue(firstRow, headers, "최저보증이율")),
                    field = "guaranteedInterestRate",
                    context = context,
                )
        val expenseRatio =
            collector.collectOrNull(
                ValueCleaner.parseDecimal(findValue(firstRow, headers, "사업비")),
                field = "expenseRatio",
                context = context,
            )

        val rawContent = rows.joinToString("|") { it.joinToString(",") }
        val checksum = crawlerCommon.calculateChecksum(rawContent.toByteArray())

        return ProductRawData(
            companyName = companyName,
            productName = productName,
            category = mapping.category,
            subcategory = mapping.subcategory,
            renewalType =
                ValueCleaner.cleanRenewalType(
                    findValue(firstRow, headers, "갱신여부")
                        ?: findValue(firstRow, headers, "갱신주기")
                        ?: findValue(firstRow, headers, "갱신"),
                ),
            salesChannel =
                findValue(firstRow, headers, "판매채널")
                    ?: findValue(firstRow, headers, "채널"),
            surrenderValues = surrenderValues,
            checksum = checksum,
            surrenderType = ValueCleaner.cleanSurrenderType(findValue(firstRow, headers, "해약환급금")),
            salesStartDate =
                collector.collectOrNull(
                    ValueCleaner.parseDate(findValue(firstRow, headers, "판매일자")),
                    field = "salesStartDate",
                    context = context,
                ),
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

    /**
     * 유지기간별 해약환급금/계약자적립액 추출 (행 방향 포함)
     *
     * 생보: 유지기간별 컬럼(가로) — 기존 extractSurrenderValues 로직
     * 손보: 유지기간별 행(세로) — "경과기간" 또는 "유지기간" 컬럼이 있으면 행 방향으로 추출
     */
    private fun extractSurrenderValuesWithRows(
        rows: List<List<String>>,
        headers: List<String>,
    ): List<SurrenderData> {
        // "경과기간" 또는 "유지기간" 컬럼이 있으면 행 방향 데이터
        val elapsedIdx =
            HtmlExcelParser.findColumnIndex(headers, "경과기간")
                ?: HtmlExcelParser.findColumnIndex(headers, "유지기간")

        if (elapsedIdx != null) {
            // 손보 행 방향: rows 전체 순회
            val result = mutableListOf<SurrenderData>()
            val surrenderIdx = HtmlExcelParser.findColumnIndex(headers, "해약환급금")
            val reserveIdx = HtmlExcelParser.findColumnIndex(headers, "계약자적립액")

            for (row in rows) {
                val elapsedText = row.getOrElse(elapsedIdx) { "" }.trim()
                // "조회된 데이터가 없습니다." 등 무의미 행 스킵 (ISSUE-9)
                if (elapsedText.isBlank() || elapsedText.contains("조회된") || elapsedText.contains("데이터가 없")) {
                    continue
                }
                val months = ValueCleaner.parseElapsedMonths(elapsedText) ?: continue
                val surrenderValue = surrenderIdx?.let { ValueCleaner.cleanAmount(row.getOrElse(it) { "" }) } ?: continue
                val reserveValue = reserveIdx?.let { ValueCleaner.cleanAmount(row.getOrElse(it) { "" }) }

                result.add(
                    SurrenderData(
                        elapsedMonths = months,
                        gender = null,
                        surrenderValue = surrenderValue,
                        policyholderReserve = reserveValue,
                    ),
                )
            }
            return result
        }

        // 생보 컬럼 방향: 기존 로직 (firstRow 기반)
        return if (rows.isNotEmpty()) extractSurrenderValues(rows.first(), headers) else emptyList()
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> = parseProtection(table, mapping, sourceUrl, collector)

    // ============================================================
    // 변액저축성 보험 (2파일) — SAVINGS 유사 + PDF URL
    // ============================================================
    private fun parseVariableSavings(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val products = parseSavings(table, mapping, sourceUrl, collector)

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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> = parseSavings(table, mapping, sourceUrl, collector)

    // ============================================================
    // 실손의료보험 (1파일) — PROTECTION 유사
    // ============================================================
    private fun parseHealth(
        table: ParsedTable,
        mapping: ExcelParserConfig.FileParserMapping,
        sourceUrl: String = "https://pub.insure.or.kr",
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val headers = table.headers
        val companyIdx =
            HtmlExcelParser.findColumnIndex(headers, "보험회사")
                ?: HtmlExcelParser.findColumnIndex(headers, "회사명")
                ?: 0
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
                buildHealthProduct(companyName, productName, rows, headers, mapping, sourceUrl, collector)
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
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): ProductRawData {
        val firstRow = rows.first()
        val context = "$companyName|$productName"

        // 급부별 담보 추출
        val coverages = rows.mapNotNull { row -> extractCoverage(row, headers) }

        val notes = findValue(firstRow, headers, "특이사항")

        // 급부별 보험료 추출 (남/여, 특이사항에서 비교 기준 조건 추출)
        val premiums = rows.flatMap { row -> extractPremiums(row, headers, notes, mapping.subcategory, collector, context) }

        // 지수 추출
        val indices = extractIndices(firstRow, headers, mapping.subcategory, rows, collector, context)

        val rawContent = rows.joinToString("|") { it.joinToString(",") }
        val checksum = crawlerCommon.calculateChecksum(rawContent.toByteArray())

        return ProductRawData(
            companyName = companyName,
            productName = productName,
            category = mapping.category,
            subcategory = mapping.subcategory,
            renewalType =
                ValueCleaner.cleanRenewalType(
                    findValue(firstRow, headers, "갱신여부")
                        ?: findValue(firstRow, headers, "갱신주기")
                        ?: findValue(firstRow, headers, "갱신"),
                ),
            salesChannel =
                findValue(firstRow, headers, "판매채널")
                    ?: findValue(firstRow, headers, "채널"),
            coverages = coverages,
            premiumExamples = premiums,
            indices = indices,
            checksum = checksum,
            salesStartDate =
                collector.collectOrNull(
                    ValueCleaner.parseDate(findValue(firstRow, headers, "판매일자")),
                    field = "salesStartDate",
                    context = context,
                ),
            notes = notes,
            phone = ValueCleaner.cleanPhone(findValue(firstRow, headers, "대표번호")),
            sourceUrl = sourceUrl,
        )
    }

    // ============================================================
    // 공통 헬퍼
    // ============================================================

    /** 나이 기준이 없는 재물보험 카테고리 (보험료 산출 기준: 건물등급/면적) */
    val ageOptionalSubcategories = setOf("PB11", "PB12")

    data class PremiumConditions(
        val age: Int?,
        val insurancePeriod: String,
        val paymentPeriod: String,
    )

    /**
     * 특이사항에서 보험료 비교 기준 조건을 추출.
     *
     * 생보 패턴: "보험료 기준: 40세, 보험기간: 종신, 납입기간: 20년납"
     * 손보 패턴:
     *   A: "40세, 상해1급, 100세만기, 20년납 기준"
     *   B: "아파트, 건물1급, 100㎡, 10년만기 전기납 기준"
     *   C: "60세, 상해1급, 1년만기 1년납"
     *   D: "5세, 1급, 100세만기, 20년납"
     *   E: "보험기간 10년, 납입기간 10년"
     *
     * @param subcategory 카테고리 코드 (PB11/PB12는 나이 없어도 됨)
     */
    fun extractPremiumConditions(
        notes: String?,
        subcategory: String? = null,
    ): PremiumConditions {
        if (notes == null) {
            throw ParsingException("특이사항 누락: 보험료 기준 조건을 확인할 수 없음")
        }

        // targetLine 찾기: "보험료", "비교기준", "기준" 키워드
        val targetLine =
            notes.lines().firstOrNull {
                it.contains("보험료") || it.contains("비교기준")
            } ?: notes.lines().firstOrNull {
                it.contains("기준")
            }

        // 생보 패턴 매칭 시도 (targetLine이 있고 "보험기간:" / "납입기간:" 형식)
        if (targetLine != null) {
            val lifeInsurancePeriod =
                Regex("보험기간[:\\s]*(\\S+)")
                    .find(targetLine)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            val lifePaymentPeriod =
                Regex("납입기간[:\\s]*(\\S+)")
                    .find(targetLine)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            if (lifeInsurancePeriod != null && lifePaymentPeriod != null) {
                val age =
                    Regex("(\\d+)세(?!만기)")
                        .find(targetLine)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                if (age == null && subcategory !in ageOptionalSubcategories) {
                    throw ParsingException("기준 나이 추출 실패: $targetLine")
                }
                return PremiumConditions(age, lifeInsurancePeriod, lifePaymentPeriod)
            }
        }

        // 손보 패턴: 전체 텍스트에서 직접 추출
        val searchText = targetLine ?: notes

        // 나이: "N세" 뒤에 "만기"가 오지 않는 경우만 나이로 인식 ("100세만기"는 보험기간)
        val age =
            Regex("(\\d+)세(?!만기)")
                .find(searchText)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()

        // 보험기간: "N년만기", "N세만기", 또는 "보험기간 N년"
        val insurancePeriod =
            Regex("(\\d+년만기|\\d+세만기)").find(searchText)?.groupValues?.get(1)
                ?: Regex("보험기간\\s*(\\d+년)").find(searchText)?.groupValues?.get(1)

        // 납입기간: "전기납", "N년납", 또는 "납입기간 N년"
        val paymentPeriod =
            Regex("(전기납|\\d+년납)").find(searchText)?.groupValues?.get(1)
                ?: Regex("납입기간\\s*(\\d+년)").find(searchText)?.groupValues?.get(1)

        if (insurancePeriod == null) {
            throw ParsingException("보험기간 추출 실패: ${searchText.take(100)}")
        }
        if (paymentPeriod == null) {
            throw ParsingException("납입기간 추출 실패: ${searchText.take(100)}")
        }

        // 나이 검증: 재물보험(PB11/PB12)이 아닌데 나이가 없으면 에러
        if (age == null && subcategory !in ageOptionalSubcategories) {
            throw ParsingException("기준 나이 추출 실패 (subcategory=$subcategory): ${searchText.take(100)}")
        }

        return PremiumConditions(age, insurancePeriod, paymentPeriod)
    }

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
