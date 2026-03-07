package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.CrawlError
import com.insuranceai.pipeline.dto.CrawlResult
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.parser.ComparisonDataParser
import com.insuranceai.policy.domain.InsuranceType
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class NonLifeInsuranceCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
    private val comparisonDataParser: ComparisonDataParser,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BASE_URL = "https://kpub.knia.or.kr"
        val CATEGORIES =
            listOf(
                "상해보험",
                "질병보험",
                "암보험",
                "운전자보험",
                "어린이보험",
                "화재보험",
                "종합보험",
                "비용보험",
                "간병/치매보험",
                "치아보험",
                "반려동물보험",
                "기타",
                "실손의료보험",
                "저축성보험",
            )
    }

    suspend fun crawlAll(categoryFilter: List<String> = emptyList()): CrawlResult {
        val allProducts = mutableListOf<ProductRawData>()
        val allErrors = mutableListOf<CrawlError>()

        val categoriesToCrawl =
            if (categoryFilter.isEmpty()) {
                CATEGORIES
            } else {
                val invalid = categoryFilter - CATEGORIES.toSet()
                if (invalid.isNotEmpty()) {
                    logger.warn("손보 알 수 없는 카테고리 무시: $invalid")
                }
                CATEGORIES.filter { it in categoryFilter }
            }

        for (category in categoriesToCrawl) {
            try {
                val products =
                    crawlerCommon.usePlaywright { browser ->
                        crawlCategory(browser, category)
                    }
                allProducts.addAll(products)
                logger.info("손보 [$category] 크롤링 완료: ${products.size}건")

                if (products.isEmpty()) {
                    logger.warn("손보 [$category] 수집 건수 0건 — 사이트 구조 변경 확인 필요")
                }
            } catch (e: Exception) {
                allErrors.add(CrawlError(category, null, e.message ?: "", "BROWSER"))
                logger.error("손보 [$category] 크롤링 실패", e)
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
        )
    }

    suspend fun crawlCategory(
        browser: Browser,
        category: String,
    ): List<ProductRawData> {
        val page = crawlerCommon.createPage(browser)
        return try {
            crawlerCommon.withRetry {
                page.navigate("$BASE_URL/comparison/main.do")
                page.waitForLoadState()
            }

            logger.debug("손보 [$category] 페이지 접근 완료")

            val excelPath = downloadExcel(page, category)
            if (excelPath != null) {
                // HTML 테이블로 파싱 (Jsoup)
                comparisonDataParser.parseFile(excelPath, category, null)
            } else {
                logger.warn("손보 [$category] 엑셀 다운로드 실패")
                emptyList()
            }
        } finally {
            page.close()
        }
    }

    suspend fun downloadExcel(
        page: Page,
        category: String,
    ): Path? =
        withContext(Dispatchers.IO) {
            try {
                val downloadDir = Path.of(config.crawl.downloadDir, "non-life")
                java.nio.file.Files
                    .createDirectories(downloadDir)

                val download =
                    page.waitForDownload {
                        page
                            .locator(
                                "button.btn_excel, a.btn_excel, " +
                                    "button:has-text('엑셀'), a:has-text('엑셀'), " +
                                    "[onclick*='excel'], [onclick*='Excel']",
                            ).first()
                            .click()
                    }
                val savePath = downloadDir.resolve("nonlife_${category.replace("/", "_")}.xls")
                download.saveAs(savePath)
                savePath
            } catch (e: Exception) {
                logger.warn("손보 [$category] 엑셀 다운로드 실패: ${e.message}")
                null
            }
        }

    @Suppress("UnusedParameter")
    suspend fun extractRemarks(
        page: Page,
        productName: String,
    ): String? =
        try {
            null
        } catch (e: Exception) {
            logger.debug("손보 특이사항 추출 실패 ($productName): ${e.message}")
            null
        }
}
