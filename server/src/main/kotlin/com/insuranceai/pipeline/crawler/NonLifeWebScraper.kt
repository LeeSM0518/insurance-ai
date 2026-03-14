package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.CrawlResult
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.parser.ParsingWarningCollector
import com.insuranceai.pipeline.parser.ValueCleaner
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 손해보험협회 비교공시 웹 페이지 스크래핑
 *
 * 기존 CRAWL 흐름(JSON API → Excel 폴백)에서 누락된 신버전 상품을 보완 수집.
 * 보장성(tabType=1) 카테고리만 지원.
 */
@Component
class NonLifeWebScraper(
    private val crawlerCommon: CrawlerCommon,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 웹 페이지에서 ProductRawData 수집.
     *
     * #list tbody.tbodyClass의 전체 테이블 데이터를 추출하여
     * ProductRawData로 변환한다.
     */
    suspend fun scrapeCategories(
        categoryFilter: List<String> = emptyList(),
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val allProducts = mutableListOf<ProductRawData>()

        // 보장성(tabType=1)만 지원
        val categoriesToScrape =
            NonLifeInsuranceCrawler.CATEGORIES
                .filter { it.tabType == 1 }
                .let { categories ->
                    if (categoryFilter.isEmpty()) {
                        categories
                    } else {
                        categories.filter { cat ->
                            categoryFilter.any { filter ->
                                cat.categoryName.contains(filter) ||
                                    cat.tptyCode == filter ||
                                    cat.tabName == filter
                            }
                        }
                    }
                }

        if (categoriesToScrape.isEmpty()) return emptyList()

        crawlerCommon.usePlaywright { browser ->
            for (category in categoriesToScrape) {
                try {
                    val products = scrapeCategory(browser, category, warningCollector)
                    allProducts.addAll(products)
                    logger.info("웹 스크래핑 [${category.tabName}/${category.categoryName}]: ${products.size}건")
                } catch (e: Exception) {
                    logger.warn("웹 스크래핑 [${category.tabName}/${category.categoryName}] 실패: ${e.message}")
                }
                crawlerCommon.randomDelay()
            }
        }

        logger.info("웹 스크래핑 전체 완료: ${allProducts.size}건")
        return allProducts
    }

    /**
     * CRAWL 결과와 비교하여 누락된 상품만 웹에서 보완 수집.
     *
     * companyName + productName 조합이 CRAWL 결과에 없는 상품만 반환.
     */
    suspend fun supplementMissing(
        categoryFilter: List<String>,
        crawlResult: CrawlResult,
    ): List<ProductRawData> {
        val existingKeys =
            crawlResult.products
                .map { "${it.companyName}::${it.productName}" }
                .toSet()

        val webProducts = scrapeCategories(categoryFilter)

        val supplemented =
            webProducts.filter {
                "${it.companyName}::${it.productName}" !in existingKeys
            }

        if (supplemented.isNotEmpty()) {
            logger.info(
                "웹 보완 결과: 웹 ${webProducts.size}건 중 ${supplemented.size}건 신규 " +
                    "(기존 CRAWL ${crawlResult.products.size}건)",
            )
        }

        return supplemented
    }

    /** 한 카테고리 페이지에서 전체 상품 데이터 스크래핑 */
    internal suspend fun scrapeCategory(
        browser: Browser,
        category: NonLifeInsuranceCrawler.NonLifeCategory,
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val page = crawlerCommon.createPage(browser)
        return try {
            val url = NonLifeInsuranceCrawler.buildCategoryUrl(category)
            crawlerCommon.withRetry {
                page.navigate(url)
                page.waitForLoadState()
            }

            // AJAX 데이터 로딩 대기
            try {
                page.waitForSelector(
                    "#list tbody.tbodyClass",
                    Page.WaitForSelectorOptions().setTimeout(15000.0),
                )
            } catch (e: Exception) {
                logger.warn("${category.categoryName} 데이터 로딩 대기 타임아웃: ${e.message}")
                return emptyList()
            }

            val rawItems = extractTableData(page)
            rawItems.mapNotNull { item ->
                convertToProductRawData(item, category, warningCollector)
            }
        } finally {
            page.close()
        }
    }

    /**
     * JavaScript로 테이블 전체 데이터 추출.
     *
     * tbody.tbodyClass 단위로 순회하여 첫 행의 기본 정보 + 하위 행의 담보 데이터를 수집.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun extractTableData(page: Page): List<Map<String, Any?>> =
        page.evaluate(
            """() => {
                const tbodies = document.querySelectorAll('#list tbody.tbodyClass');
                return Array.from(tbodies).map(tb => {
                    const rows = tb.querySelectorAll('tr');
                    if (rows.length === 0) return null;

                    const firstRow = rows[0];
                    const tds = firstRow.querySelectorAll('td');

                    // 담보 데이터 수집 (첫 행 + 하위 행)
                    const coverages = [];

                    // 첫 행의 담보 (td[4], td[5], td[6])
                    if (tds.length > 6) {
                        const name = tds[4] ? tds[4].textContent.trim() : '';
                        const reason = tds[5] ? tds[5].textContent.trim() : '';
                        const amount = tds[6] ? tds[6].textContent.trim() : '';
                        if (name) {
                            coverages.push({ name, reason, amount });
                        }
                    }

                    // 하위 행의 담보
                    for (let i = 1; i < rows.length; i++) {
                        const subTds = rows[i].querySelectorAll('td');
                        if (subTds.length >= 2) {
                            const name = subTds[0] ? subTds[0].textContent.trim() : '';
                            const reason = subTds[1] ? subTds[1].textContent.trim() : '';
                            const amount = subTds.length > 2 ? subTds[2].textContent.trim() : '';
                            if (name) {
                                coverages.push({ name, reason, amount });
                            }
                        }
                    }

                    // hidden input 추출
                    const summarySeqInput = tb.querySelector('input[name=summary_seq]');
                    const tpCodeInput = tb.querySelector('input[name=tp_code]');

                    return {
                        company: tds.length > 1 ? tds[1].textContent.trim() : '',
                        product: tds.length > 2 ? tds[2].textContent.trim() : '',
                        channel: tds.length > 3 ? tds[3].textContent.trim() : '',
                        appliedRate: tds.length > 7 ? tds[7].textContent.trim() : '',
                        guaranteedRate: tds.length > 8 ? tds[8].textContent.trim() : '',
                        premiumMale: tds.length > 9 ? tds[9].textContent.trim() : '',
                        premiumFemale: tds.length > 10 ? tds[10].textContent.trim() : '',
                        priceIndexMale: tds.length > 12 ? tds[12].textContent.trim() : '',
                        priceIndexFemale: tds.length > 13 ? tds[13].textContent.trim() : '',
                        extraPremiumIndex: tds.length > 14 ? tds[14].textContent.trim() : '',
                        contractCostIndex: tds.length > 15 ? tds[15].textContent.trim() : '',
                        renewalType: tds.length > 18 ? tds[18].textContent.trim() : '',
                        notes: tds.length > 20 ? tds[20].textContent.trim() : '',
                        phone: tds.length > 21 ? tds[21].textContent.trim() : '',
                        coverages: coverages,
                        summarySeq: summarySeqInput ? summarySeqInput.value : null,
                        tpCode: tpCodeInput ? tpCodeInput.value : null
                    };
                }).filter(item => item && item.company && item.product);
            }""",
        ) as? List<Map<String, Any?>> ?: emptyList()

    /** 추출된 원시 데이터를 ProductRawData로 변환 */
    internal fun convertToProductRawData(
        item: Map<String, Any?>,
        category: NonLifeInsuranceCrawler.NonLifeCategory,
        warningCollector: ParsingWarningCollector,
    ): ProductRawData? {
        val companyName = item["company"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val productName = item["product"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val context = "$companyName|$productName"

        // 담보 추출
        @Suppress("UNCHECKED_CAST")
        val rawCoverages = item["coverages"] as? List<Map<String, Any?>> ?: emptyList()
        val coverages =
            rawCoverages.mapNotNull { cov ->
                val name = cov["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CoverageData(
                    coverageName = name,
                    paymentReason = cov["reason"]?.toString()?.trim() ?: "",
                    paymentAmount = cov["amount"]?.toString()?.trim() ?: "",
                    sectionType = "MAIN",
                )
            }

        // 보험료 추출
        val premiums = mutableListOf<PremiumData>()
        val maleBill =
            warningCollector.collectOrNull(
                ValueCleaner.parseAmount(item["premiumMale"]?.toString()),
                field = "premiumMale",
                context = context,
            )
        if (maleBill != null && maleBill > 0) {
            premiums.add(PremiumData("남", 40, "", "", 0L, premium = maleBill))
        }
        val femaleBill =
            warningCollector.collectOrNull(
                ValueCleaner.parseAmount(item["premiumFemale"]?.toString()),
                field = "premiumFemale",
                context = context,
            )
        if (femaleBill != null && femaleBill > 0) {
            premiums.add(PremiumData("여", 40, "", "", 0L, premium = femaleBill))
        }

        // 지수 추출
        val indices = mutableListOf<IndexData>()
        val today = LocalDate.now()
        warningCollector
            .collectOrNull(
                ValueCleaner.parseDecimal(item["priceIndexMale"]?.toString()),
                field = "priceIndexMale",
                context = context,
            )?.let { indices.add(IndexData("PRICE", "남", null, it, today)) }
        warningCollector
            .collectOrNull(
                ValueCleaner.parseDecimal(item["priceIndexFemale"]?.toString()),
                field = "priceIndexFemale",
                context = context,
            )?.let { indices.add(IndexData("PRICE", "여", null, it, today)) }
        warningCollector
            .collectOrNull(
                ValueCleaner.parseDecimal(item["contractCostIndex"]?.toString()),
                field = "contractCostIndex",
                context = context,
            )?.let { indices.add(IndexData("CONTRACT_COST", null, null, it, today)) }
        warningCollector
            .collectOrNull(
                ValueCleaner.parseDecimal(item["extraPremiumIndex"]?.toString()),
                field = "extraPremiumIndex",
                context = context,
            )?.let { indices.add(IndexData("EXTRA_PREMIUM", null, null, it, today)) }

        // checksum 생성
        val rawContent =
            listOf(
                companyName,
                productName,
                item["channel"],
                item["appliedRate"],
                item["guaranteedRate"],
                item["premiumMale"],
                item["premiumFemale"],
                item["renewalType"],
                item["notes"],
            ).joinToString("|")
        val checksum = crawlerCommon.calculateChecksum(rawContent.toByteArray())

        val parserCategory = NonLifeInsuranceCrawler.resolveParserCategory(category)

        return ProductRawData(
            companyName = companyName,
            productName = productName,
            category = parserCategory,
            subcategory = category.tptyCode,
            salesChannel = item["channel"]?.toString()?.trim()?.takeIf { it.isNotBlank() },
            coverages = coverages,
            premiumExamples = premiums,
            indices = indices,
            checksum = checksum,
            renewalType = ValueCleaner.cleanRenewalType(item["renewalType"]?.toString()),
            notes = ValueCleaner.cleanString(item["notes"]?.toString()),
            phone = ValueCleaner.cleanPhone(item["phone"]?.toString()),
            appliedInterestRate =
                warningCollector.collectOrNull(
                    ValueCleaner.parseDecimal(item["appliedRate"]?.toString()),
                    field = "appliedInterestRate",
                    context = context,
                ),
            guaranteedInterestRate =
                warningCollector.collectOrNull(
                    ValueCleaner.parseDecimal(item["guaranteedRate"]?.toString()),
                    field = "guaranteedInterestRate",
                    context = context,
                ),
            sourceUrl = "https://kpub.knia.or.kr",
        )
    }
}
