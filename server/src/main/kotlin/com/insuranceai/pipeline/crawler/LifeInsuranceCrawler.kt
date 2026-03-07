package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.CoverageData
import com.insuranceai.pipeline.dto.CrawlError
import com.insuranceai.pipeline.dto.CrawlResult
import com.insuranceai.pipeline.dto.IndexData
import com.insuranceai.pipeline.dto.PremiumData
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.policy.domain.InsuranceType
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class LifeInsuranceCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BASE_URL = "https://pub.insure.or.kr"
        val CATEGORIES =
            listOf(
                "종신보험",
                "정기보험",
                "질병보험",
                "암보험",
                "CI보험",
                "상해보험",
                "어린이보험",
                "치아보험",
                "간병/치매보험",
                "기타",
                "저축성보험",
                "변액보험(보장성)",
                "변액보험(저축성)",
                "연금저축",
                "실손의료보험",
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
                    logger.warn("생보 알 수 없는 카테고리 무시: $invalid")
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
                logger.info("생보 [$category] 크롤링 완료: ${products.size}건")

                if (products.isEmpty()) {
                    logger.warn("생보 [$category] 수집 건수 0건 — 사이트 구조 변경 확인 필요")
                }
            } catch (e: Exception) {
                allErrors.add(CrawlError(category, null, e.message ?: "", "BROWSER"))
                logger.error("생보 [$category] 크롤링 실패", e)
            }
            crawlerCommon.randomDelay()
        }

        return CrawlResult(
            insuranceType = InsuranceType.LIFE,
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
                page.navigate("$BASE_URL/pubCompareMvp.do")
                page.waitForLoadState()
            }

            // 카테고리 선택 및 검색 (실제 셀렉터는 사이트 확인 후 조정 필요)
            logger.debug("생보 [$category] 페이지 접근 완료")

            // 엑셀 다운로드 시도
            val excelPath = downloadExcel(page, category)
            if (excelPath != null) {
                parseExcel(excelPath, category)
            } else {
                logger.warn("생보 [$category] 엑셀 다운로드 실패")
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
                val downloadDir = Path.of(config.crawl.downloadDir, "life")
                java.nio.file.Files
                    .createDirectories(downloadDir)

                // 엑셀 다운로드 버튼 클릭 (셀렉터는 실제 사이트 확인 후 조정)
                val download =
                    page.waitForDownload {
                        page.locator("button:has-text('엑셀'), a:has-text('엑셀')").first().click()
                    }
                val savePath = downloadDir.resolve("life_${category.replace("/", "_")}.xlsx")
                download.saveAs(savePath)
                savePath
            } catch (e: Exception) {
                logger.warn("생보 [$category] 엑셀 다운로드 실패: ${e.message}")
                null
            }
        }

    fun parseExcel(
        excelPath: Path,
        category: String,
    ): List<ProductRawData> =
        try {
            val workbook = WorkbookFactory.create(excelPath.toFile())
            val sheet = workbook.getSheetAt(0)
            val products = mutableListOf<ProductRawData>()

            // 헤더 행 건너뛰기 (첫 번째 행은 보통 헤더)
            val rows =
                sheet
                    .iterator()
                    .asSequence()
                    .drop(1)
                    .toList()

            for (row in rows) {
                try {
                    val companyName = row.getCell(0)?.stringCellValue?.trim() ?: continue
                    val productName = row.getCell(1)?.stringCellValue?.trim() ?: continue

                    if (companyName.isBlank() || productName.isBlank()) continue

                    val rawBytes = "$companyName|$productName|$category".toByteArray()
                    val checksum = crawlerCommon.calculateChecksum(rawBytes)

                    // 보험료 데이터 파싱 (컬럼 위치는 실제 엑셀 확인 후 조정)
                    val premiumExamples = parsePremiumColumns(row)
                    val indices = parseIndexColumns(row)
                    val coverages = parseCoverageColumns(row)

                    products.add(
                        ProductRawData(
                            companyName = companyName,
                            productName = productName,
                            category = category,
                            renewalType = row.getCell(2)?.stringCellValue?.trim(),
                            coverages = coverages,
                            premiumExamples = premiumExamples,
                            indices = indices,
                            checksum = checksum,
                            sourceUrl = "$BASE_URL/pubCompareMvp.do",
                        ),
                    )
                } catch (e: Exception) {
                    logger.warn("생보 [$category] 행 파싱 실패: ${e.message}")
                }
            }

            workbook.close()
            products
        } catch (e: Exception) {
            logger.error("생보 [$category] 엑셀 파싱 실패: ${e.message}")
            emptyList()
        }

    @Suppress("UnusedParameter")
    private fun parsePremiumColumns(row: Row): List<PremiumData> {
        // 실제 엑셀 컬럼 구조에 맞게 조정 필요
        return emptyList()
    }

    @Suppress("UnusedParameter")
    private fun parseIndexColumns(row: Row): List<IndexData> {
        // 실제 엑셀 컬럼 구조에 맞게 조정 필요
        return emptyList()
    }

    @Suppress("UnusedParameter")
    private fun parseCoverageColumns(row: Row): List<CoverageData> {
        // 실제 엑셀 컬럼 구조에 맞게 조정 필요
        return emptyList()
    }

    @Suppress("UnusedParameter")
    suspend fun downloadPdfSummaries(
        page: Page,
        products: List<ProductRawData>,
    ): Map<String, Path> {
        val result = mutableMapOf<String, Path>()
        // PDF 다운로드 로직 (실제 사이트 확인 후 구현)
        return result
    }
}
