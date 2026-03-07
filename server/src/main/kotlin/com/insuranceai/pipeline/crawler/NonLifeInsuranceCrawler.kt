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
class NonLifeInsuranceCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
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

    suspend fun crawlAll(): CrawlResult {
        val allProducts = mutableListOf<ProductRawData>()
        val allErrors = mutableListOf<CrawlError>()

        for (category in CATEGORIES) {
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
                parseExcel(excelPath, category)
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
                        page.locator("button:has-text('엑셀'), a:has-text('엑셀')").first().click()
                    }
                val savePath = downloadDir.resolve("nonlife_${category.replace("/", "_")}.xlsx")
                download.saveAs(savePath)
                savePath
            } catch (e: Exception) {
                logger.warn("손보 [$category] 엑셀 다운로드 실패: ${e.message}")
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

                    // 손보 전용 파싱: 남/여 별도 보험료, 담보별 지수 등
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
                            sourceUrl = "$BASE_URL/comparison/main.do",
                        ),
                    )
                } catch (e: Exception) {
                    logger.warn("손보 [$category] 행 파싱 실패: ${e.message}")
                }
            }

            workbook.close()
            products
        } catch (e: Exception) {
            logger.error("손보 [$category] 엑셀 파싱 실패: ${e.message}")
            emptyList()
        }

    @Suppress("UnusedParameter")
    suspend fun extractRemarks(
        page: Page,
        productName: String,
    ): String? =
        try {
            // 손보 전용: 특이사항 팝업 텍스트 추출 (셀렉터는 실제 사이트 확인 후 조정)
            null
        } catch (e: Exception) {
            logger.debug("손보 특이사항 추출 실패 ($productName): ${e.message}")
            null
        }

    @Suppress("UnusedParameter")
    private fun parsePremiumColumns(row: Row): List<PremiumData> {
        // 손보 전용: 남/여 각각 별도 보험료 (생보와 다른 컬럼 구조)
        return emptyList()
    }

    @Suppress("UnusedParameter")
    private fun parseIndexColumns(row: Row): List<IndexData> {
        // 손보 전용: 남/여 별도 가격지수, 담보별 보장범위지수
        return emptyList()
    }

    @Suppress("UnusedParameter")
    private fun parseCoverageColumns(row: Row): List<CoverageData> = emptyList()
}
