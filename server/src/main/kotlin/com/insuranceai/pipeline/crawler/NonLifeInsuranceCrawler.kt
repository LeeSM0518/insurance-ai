package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.CrawlError
import com.insuranceai.pipeline.dto.CrawlResult
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.parser.ComparisonDataParser
import com.insuranceai.pipeline.parser.NonLifeJsonParser
import com.insuranceai.pipeline.parser.ParsingWarningCollector
import com.insuranceai.policy.domain.InsuranceType
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * 손해보험협회 비교공시 크롤러
 *
 * 비교공시 팝업 페이지에서 카테고리별 엑셀 다운로드.
 * URL 파라미터(tabType, tptyCode)로 카테고리 직접 접근.
 * 엑셀 파일(.xls)은 실제 HTML 테이블 형식 — Jsoup으로 파싱.
 */
@Component
class NonLifeInsuranceCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
    private val comparisonDataParser: ComparisonDataParser,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 손보 카테고리 정보
     *
     * @param tabType 상위 탭 번호 (1~4)
     * @param tabName 탭 표시명
     * @param tptyCode 카테고리 코드 (PB11, PC11 등)
     * @param categoryName 카테고리 표시명
     */
    data class NonLifeCategory(
        val tabType: Int,
        val tabName: String,
        val tptyCode: String,
        val categoryName: String,
    )

    companion object {
        const val BASE_URL = "https://kpub.knia.or.kr"

        val CATEGORIES =
            listOf(
                // 장기보장성 (tabType=1)
                NonLifeCategory(1, "장기보장성", "PB11", "화재"),
                NonLifeCategory(1, "장기보장성", "PB12", "종합"),
                NonLifeCategory(1, "장기보장성", "PB13", "운전자"),
                NonLifeCategory(1, "장기보장성", "PB24", "어린이"),
                NonLifeCategory(1, "장기보장성", "PB25", "치아"),
                NonLifeCategory(1, "장기보장성", "PB16", "간병_치매"),
                NonLifeCategory(1, "장기보장성", "PB22", "암"),
                NonLifeCategory(1, "장기보장성", "PB14", "상해"),
                NonLifeCategory(1, "장기보장성", "PB15", "질병"),
                NonLifeCategory(1, "장기보장성", "PB18", "비용"),
                NonLifeCategory(1, "장기보장성", "PB27", "반려동물"),
                NonLifeCategory(1, "장기보장성", "PB17", "기타"),
                // 장기저축성 (tabType=2)
                NonLifeCategory(2, "장기저축성", "PC11", "장기화재_및_종합보험"),
                NonLifeCategory(2, "장기저축성", "PC12", "장기상해보험_및_기타"),
                // 실손의료보험 (tabType=3)
                NonLifeCategory(3, "실손의료보험", "PB26", "실손의료보험_4세대"),
                NonLifeCategory(3, "실손의료보험", "PB21", "노후실손의료보험"),
                NonLifeCategory(3, "실손의료보험", "PB23", "유병력자실손보험"),
                // 연금저축 (tabType=4)
                NonLifeCategory(4, "연금저축", "PC99", "연금저축"),
            )

        /** 카테고리 URL 생성 */
        fun buildCategoryUrl(category: NonLifeCategory): String =
            "$BASE_URL/popup/disclosurePopup.do?tabType=${category.tabType}&tptyCode=${category.tptyCode}&detailYn=Y"

        /** tabType → 파서 카테고리 매핑 */
        fun resolveParserCategory(category: NonLifeCategory): String =
            when (category.tabType) {
                1 -> "NL_PROTECTION"
                2 -> "NL_SAVINGS"
                3 -> "NL_HEALTH"
                4 -> "NL_PENSION"
                else -> "NL_PROTECTION"
            }
    }

    suspend fun crawlAll(categoryFilter: List<String> = emptyList()): CrawlResult {
        val allProducts = mutableListOf<ProductRawData>()
        val allErrors = mutableListOf<CrawlError>()
        val batchCollector = ParsingWarningCollector()

        val categoriesToCrawl =
            if (categoryFilter.isEmpty()) {
                CATEGORIES
            } else {
                CATEGORIES.filter { cat ->
                    categoryFilter.any { filter ->
                        cat.categoryName.contains(filter) ||
                            cat.tptyCode == filter ||
                            cat.tabName == filter
                    }
                }
            }

        for (category in categoriesToCrawl) {
            try {
                // 1차: JSON API
                val products = crawlCategoryViaJson(category, batchCollector)
                if (products != null) {
                    allProducts.addAll(products)
                    logger.info("손보 [${category.tabName}/${category.categoryName}] JSON 크롤링 완료: ${products.size}건")
                } else {
                    // 2차: Excel 폴백 (Playwright 사용)
                    logger.info("손보 [${category.tabName}/${category.categoryName}] JSON 실패, Excel 폴백")
                    val excelProducts =
                        crawlerCommon.usePlaywright { browser ->
                            crawlCategoryViaExcel(browser, category, batchCollector)
                        }
                    allProducts.addAll(excelProducts)
                    logger.info("손보 [${category.tabName}/${category.categoryName}] Excel 크롤링 완료: ${excelProducts.size}건")
                }
            } catch (e: Exception) {
                allErrors.add(CrawlError(category.categoryName, null, e.message ?: "", "CRAWL"))
                logger.error("손보 [${category.tabName}/${category.categoryName}] 크롤링 실패", e)
            }
            crawlerCommon.randomDelay()
        }

        return CrawlResult(
            insuranceType = InsuranceType.NON_LIFE,
            totalProducts = allProducts.size,
            successCount = allProducts.size,
            failureCount = allErrors.size,
            products = allProducts,
            errors = allErrors,
            parsingWarnings = batchCollector.warnings,
            parsingFailureCount = batchCollector.failureCount,
        )
    }

    /**
     * JSON API로 카테고리 데이터 수집
     *
     * @return 성공 시 ProductRawData 리스트 (0건 포함), 실패 시 null
     */
    internal suspend fun crawlCategoryViaJson(
        category: NonLifeCategory,
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData>? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/popup/disclosureList.do"
                val body = "tabType=${category.tabType}&tptyCode=${category.tptyCode}&detailYn=Y"

                val connection =
                    java.net
                        .URI(url)
                        .toURL()
                        .openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    logger.warn("손보 [${category.categoryName}] JSON API 응답 코드: $responseCode")
                    return@withContext null
                }

                val rawJson = connection.inputStream.bufferedReader().readText()
                val items = NonLifeJsonParser.parseJson5(rawJson)
                NonLifeJsonParser.toProductRawData(items, category, crawlerCommon::calculateChecksum, warningCollector)
            } catch (e: Exception) {
                logger.warn("손보 [${category.categoryName}] JSON API 호출 실패: ${e.message}")
                null
            }
        }

    /**
     * Excel 다운로드 폴백 (Playwright 필요)
     */
    private suspend fun crawlCategoryViaExcel(
        browser: Browser,
        category: NonLifeCategory,
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val page = crawlerCommon.createPage(browser)
        return try {
            val url = buildCategoryUrl(category)
            crawlerCommon.withRetry {
                page.navigate(url)
                page.waitForLoadState()
            }

            val excelPath = downloadExcel(page, category)
            if (excelPath != null) {
                val parserCategory = resolveParserCategory(category)
                comparisonDataParser.parseFile(excelPath, parserCategory, category.tptyCode, warningCollector)
            } else {
                emptyList()
            }
        } finally {
            page.close()
        }
    }

    /**
     * Excel 다운로드 (waitForDownload만 사용)
     */
    private suspend fun downloadExcel(
        page: Page,
        category: NonLifeCategory,
    ): Path? =
        withContext(Dispatchers.IO) {
            try {
                val downloadDir = crawlerCommon.buildDatedDownloadDir("non-life")
                val savePath = downloadDir.resolve("nonlife_${category.tptyCode}_${category.categoryName}.xls")

                if (Files.exists(savePath) && Files.size(savePath) > 0) {
                    logger.info("손보 [${category.categoryName}] 이미 다운로드됨, 스킵: $savePath")
                    return@withContext savePath
                }

                val download =
                    page.waitForDownload(
                        Page.WaitForDownloadOptions().setTimeout(60_000.0),
                    ) {
                        page
                            .locator("a:has-text('엑셀다운로드'), [onclick*='exceldown']")
                            .first()
                            .click()
                    }
                download.saveAs(savePath)
                logger.info("손보 [${category.categoryName}] 엑셀 다운로드 완료: $savePath")
                savePath
            } catch (e: Exception) {
                logger.warn("손보 [${category.categoryName}] 엑셀 다운로드 실패: ${e.message}")
                null
            }
        }
}
