package com.insuranceai.pipeline.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler
import com.insuranceai.pipeline.crawler.NonLifeInsuranceCrawler.NonLifeCategory
import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.dto.SurrenderData
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 손보협회 JSON5 응답 파싱 및 ProductRawData 변환
 *
 * disclosureList.do API가 반환하는 JSON5 형식을 표준 JSON으로 변환 후 파싱.
 */
object NonLifeJsonParser {
    private val objectMapper = ObjectMapper()

    /**
     * JSON5 응답 -> Map 리스트 변환
     *
     * @param rawJson disclosureList.do 응답 원문
     * @return 행 데이터 리스트 (필드명 -> 값)
     */
    fun parseJson5(rawJson: String): List<Map<String, Any>> {
        if (rawJson.isBlank() || rawJson == "{list:[]}") return emptyList()

        // JSON5 -> 표준 JSON 변환
        var standardJson = rawJson
        // 값 내부의 줄바꿈을 이스케이프 (단일따옴표 문자열 안의 다중 개행 처리)
        while (standardJson.contains(Regex("'[^']*\n[^']*'"))) {
            standardJson =
                standardJson.replace(Regex("'([^']*)\n([^']*)'")) {
                    "'${it.groupValues[1]}\\n${it.groupValues[2]}'"
                }
        }
        standardJson =
            standardJson
                // 단일따옴표 -> 이중따옴표
                .replace("'", "\"")
                // 따옴표 없는 키에 따옴표 추가
                .replace(Regex("(\\{|,)\\s*(\\w+)\\s*:")) {
                    "${it.groupValues[1]}\"${it.groupValues[2]}\":"
                }

        val root = objectMapper.readValue(standardJson, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        return (root["list"] as? List<Map<String, Any>>) ?: emptyList()
    }

    /**
     * JSON 데이터 -> ProductRawData 리스트 변환
     *
     * @param items parseJson5 결과
     * @param category 카테고리 정보
     * @param checksumCalculator checksum 계산 함수
     */
    fun toProductRawData(
        items: List<Map<String, Any>>,
        category: NonLifeCategory,
        checksumCalculator: (ByteArray) -> String,
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        if (items.isEmpty()) return emptyList()

        return when (category.tabType) {
            1, 3 -> convertProtectionOrHealth(items, category, checksumCalculator, warningCollector)
            2 -> convertSavings(items, category, checksumCalculator, warningCollector)
            4 -> convertPension(items, category, checksumCalculator, warningCollector)
            else -> emptyList()
        }
    }

    /** 장기보장성/실손의료 변환 (tabType=1, 3) */
    private fun convertProtectionOrHealth(
        items: List<Map<String, Any>>,
        category: NonLifeCategory,
        checksumCalculator: (ByteArray) -> String,
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        // TP_CODE로 그룹핑 (동일 상품의 담보 행들)
        val grouped =
            items
                .groupBy { it["TP_CODE"] as? String ?: "" }
                .filter { it.key.isNotBlank() }

        return grouped.mapNotNull { (_, rows) ->
            val first = rows.first()

            val companyName = first.str("P_CODE_NM") ?: return@mapNotNull null
            val productName = first.str("TP_NAME") ?: return@mapNotNull null
            val context = "$companyName|$productName"

            // 담보 추출
            val coverages =
                rows.mapNotNull { row ->
                    val name = row.str("TP_PAY_NAME") ?: return@mapNotNull null
                    CoverageData(
                        coverageName = name,
                        paymentReason = row.str("TP_PAY_REASON") ?: "",
                        paymentAmount = row.str("TP_PAY") ?: "",
                        sectionType = "MAIN",
                    )
                }

            // 보험료 추출
            val premiums = mutableListOf<PremiumData>()
            val maleBill =
                collector.collectOrNull(
                    ValueCleaner.parseAmount(first.str("TP_M_BILL")),
                    field = "TP_M_BILL",
                    context = context,
                )
            if (maleBill != null && maleBill > 0) {
                premiums.add(PremiumData("남", 40, "", "", 0L, maleBill))
            }
            val femaleBill =
                collector.collectOrNull(
                    ValueCleaner.parseAmount(first.str("TP_W_BILL")),
                    field = "TP_W_BILL",
                    context = context,
                )
            if (femaleBill != null && femaleBill > 0) {
                premiums.add(PremiumData("여", 40, "", "", 0L, femaleBill))
            }

            // 지수 추출
            val indices = mutableListOf<IndexData>()
            val today = LocalDate.now()
            collector
                .collectOrNull(
                    ValueCleaner.parseDecimal(first.str("INS_PAY_INDEX_M")),
                    field = "INS_PAY_INDEX_M",
                    context = context,
                )?.let { indices.add(IndexData("PRICE", "남", null, it, today)) }
            collector
                .collectOrNull(
                    ValueCleaner.parseDecimal(first.str("INS_PAY_INDEX_W")),
                    field = "INS_PAY_INDEX_W",
                    context = context,
                )?.let { indices.add(IndexData("PRICE", "여", null, it, today)) }
            collector
                .collectOrNull(
                    ValueCleaner.parseDecimal(first.str("CNTRCT_CNCLS_IDX")),
                    field = "CNTRCT_CNCLS_IDX",
                    context = context,
                )?.let { indices.add(IndexData("CONTRACT_COST", null, null, it, today)) }
            collector
                .collectOrNull(
                    ValueCleaner.parseDecimal(first.str("ADD_INSRNC_IDX")),
                    field = "ADD_INSRNC_IDX",
                    context = context,
                )?.let { indices.add(IndexData("EXTRA_PREMIUM", null, null, it, today)) }

            val parserCategory = NonLifeInsuranceCrawler.resolveParserCategory(category)
            val subcategory = category.tptyCode

            val rawContent =
                rows.joinToString("|") {
                    it.entries.joinToString(",") { e -> "${e.key}=${e.value}" }
                }
            val checksum = checksumCalculator(rawContent.toByteArray())

            ProductRawData(
                companyName = companyName,
                productName = productName,
                category = parserCategory,
                subcategory = subcategory,
                salesChannel = first.str("TP_NEW_CHANNEL"),
                coverages = coverages,
                premiumExamples = premiums,
                indices = indices,
                checksum = checksum,
                renewalType =
                    when (first.str("REFRESH_YN")) {
                        "Y" -> "RENEWAL"
                        "N" -> "NON_RENEWAL"
                        else -> null
                    },
                notes = first.str("TP_ETC"),
                phone = ValueCleaner.cleanPhone(first.str("ETC5")),
                appliedInterestRate =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("TP_RATE1")),
                        field = "appliedInterestRate",
                        context = context,
                    ),
                guaranteedInterestRate =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("TP_RATE2")),
                        field = "guaranteedInterestRate",
                        context = context,
                    ),
                sourceUrl = "https://kpub.knia.or.kr",
            )
        }
    }

    /** 장기저축성 변환 (tabType=2) */
    private fun convertSavings(
        items: List<Map<String, Any>>,
        category: NonLifeCategory,
        checksumCalculator: (ByteArray) -> String,
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val grouped =
            items
                .groupBy { it["TP_CODE"] as? String ?: "" }
                .filter { it.key.isNotBlank() }

        return grouped.mapNotNull { (_, rows) ->
            val first = rows.first()
            val companyName = first.str("P_CODE_NM") ?: return@mapNotNull null
            val productName = first.str("TP_NAME") ?: return@mapNotNull null
            val context = "$companyName|$productName"

            val rawContent =
                rows.joinToString("|") {
                    it.entries.joinToString(",") { e -> "${e.key}=${e.value}" }
                }

            ProductRawData(
                companyName = companyName,
                productName = productName,
                category = NonLifeInsuranceCrawler.resolveParserCategory(category),
                subcategory = category.tptyCode,
                salesChannel = first.str("TP_NEW_CHANNEL"),
                checksum = checksumCalculator(rawContent.toByteArray()),
                declaredInterestRate =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("TP_RATE1")),
                        field = "declaredInterestRate",
                        context = context,
                    ),
                guaranteedInterestRate =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("TP_RATE2")),
                        field = "guaranteedInterestRate",
                        context = context,
                    ),
                notes = first.str("TP_ETC"),
                phone = ValueCleaner.cleanPhone(first.str("ETC5")),
                sourceUrl = "https://kpub.knia.or.kr",
            )
        }
    }

    /** 연금저축 변환 (tabType=4) */
    private fun convertPension(
        items: List<Map<String, Any>>,
        category: NonLifeCategory,
        checksumCalculator: (ByteArray) -> String,
        collector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val grouped =
            items
                .groupBy { it["TP_CODE"] as? String ?: "" }
                .filter { it.key.isNotBlank() }

        return grouped.mapNotNull { (_, rows) ->
            val first = rows.first()
            val companyName = first.str("P_CODE_NM") ?: return@mapNotNull null
            val productName = first.str("TP_NAME") ?: return@mapNotNull null
            val context = "$companyName|$productName"

            // 연금저축: 환급금
            val surrenderValues =
                rows.mapNotNull { row ->
                    val refund = row.long("TP_M_REFUND") ?: return@mapNotNull null
                    SurrenderData(
                        elapsedMonths = 0,
                        gender = "남",
                        surrenderValue = refund,
                    )
                }

            val rawContent =
                rows.joinToString("|") {
                    it.entries.joinToString(",") { e -> "${e.key}=${e.value}" }
                }

            ProductRawData(
                companyName = companyName,
                productName = productName,
                category = NonLifeInsuranceCrawler.resolveParserCategory(category),
                subcategory = category.tptyCode,
                salesChannel = first.str("TP_NEW_CHANNEL"),
                surrenderValues = surrenderValues,
                checksum = checksumCalculator(rawContent.toByteArray()),
                expenseRatio =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("BUSINESS_RATE")),
                        field = "expenseRatio",
                        context = context,
                    ),
                declaredInterestRate =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("TP_RATE1")),
                        field = "declaredInterestRate",
                        context = context,
                    ),
                guaranteedInterestRate =
                    collector.collectOrNull(
                        ValueCleaner.parseDecimal(first.str("TP_RATE2")),
                        field = "guaranteedInterestRate",
                        context = context,
                    ),
                notes = first.str("TP_ETC"),
                phone = ValueCleaner.cleanPhone(first.str("ETC5")),
                sourceUrl = "https://kpub.knia.or.kr",
            )
        }
    }

    /** Map에서 문자열 값 추출 */
    private fun Map<String, Any>.str(key: String): String? {
        val v = this[key] ?: return null
        val s = v.toString().trim()
        return if (ValueCleaner.isNull(s)) null else s
    }

    /** Map에서 Long 값 추출 */
    private fun Map<String, Any>.long(key: String): Long? =
        when (val v = this[key]) {
            is Number -> v.toLong()
            is String -> ValueCleaner.cleanAmount(v)
            else -> null
        }

    /** Map에서 BigDecimal 값 추출 */
    private fun Map<String, Any>.decimal(key: String): BigDecimal? =
        when (val v = this[key]) {
            is Number -> BigDecimal(v.toString())
            is String -> ValueCleaner.cleanDecimal(v)
            else -> null
        }
}
