package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.CrawlError
import com.insuranceai.pipeline.dto.CrawlResult
import com.insuranceai.pipeline.dto.ProductRawData
import com.insuranceai.pipeline.parser.ComparisonDataParser
import com.insuranceai.pipeline.parser.ParsingWarningCollector
import com.insuranceai.policy.domain.InsuranceType
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * 생명보험협회 비교공시 크롤러
 *
 * 비교공시 엑셀 파일(.xls)은 확장자만 .xls이고 실제 HTML 테이블 형식.
 * Playwright로 다운로드 → Jsoup으로 HTML 파싱.
 */
@Component
class LifeInsuranceCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
    private val comparisonDataParser: ComparisonDataParser,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BASE_URL = "https://pub.insure.or.kr"

        /**
         * 카테고리 → (대분류, 소분류 URL 파라미터) 매핑
         *
         * 생보협회 비교공시 페이지 구조:
         * - 보장성보험: /compareDis/prodCompare/assurance/listNew.do
         * - 저축성보험: /compareDis/prodCompare/saving/list.do
         * - 변액보험(보장성): /compareDis/variableInsrn/prodCompare/assurance/listNew.do
         * - 변액보험(저축성): /compareDis/variableInsrn/prodCompare/saving/list.do
         * - 연금저축: /compareDis/prodCompare/pension/list.do
         */
        val CATEGORIES =
            listOf(
                // 보장성 보험 소분류
                CategoryInfo("종신보험", "PROTECTION", "WHOLE_LIFE", "assurance"),
                CategoryInfo("정기보험", "PROTECTION", "TERM_LIFE", "assurance"),
                CategoryInfo("질병보험", "PROTECTION", "DISEASE", "assurance"),
                CategoryInfo("암보험", "PROTECTION", "CANCER", "assurance"),
                CategoryInfo("CI보험", "PROTECTION", "CI", "assurance"),
                CategoryInfo("상해보험", "PROTECTION", "ACCIDENT", "assurance"),
                CategoryInfo("어린이보험", "PROTECTION", "CHILD", "assurance"),
                CategoryInfo("치아보험", "PROTECTION", "DENTAL", "assurance"),
                CategoryInfo("간병/치매보험", "PROTECTION", "NURSING", "assurance"),
                CategoryInfo("기타", "PROTECTION", "OTHER_PROTECTION", "assurance"),
                // 저축성 보험
                CategoryInfo("저축성_연금", "SAVINGS", "PENSION_SAVINGS", "saving"),
                CategoryInfo("저축성_저축", "SAVINGS", "SAVINGS", "saving"),
                // 변액보험(보장성)
                CategoryInfo("변액보험_보장성_종신", "VARIABLE_PROTECTION", "WHOLE_LIFE", "variable_assurance"),
                CategoryInfo("변액보험_보장성_정기", "VARIABLE_PROTECTION", "TERM_LIFE", "variable_assurance"),
                CategoryInfo("변액보험_보장성_CI", "VARIABLE_PROTECTION", "CI", "variable_assurance"),
                // 변액보험(저축성)
                CategoryInfo("변액보험_저축성_연금", "VARIABLE_SAVINGS", "PENSION_SAVINGS", "variable_saving"),
                CategoryInfo("변액보험_저축성_저축", "VARIABLE_SAVINGS", "SAVINGS", "variable_saving"),
                // 연금저축
                CategoryInfo("연금저축", "PENSION", null, "pension"),
                // 실손의료보험
                CategoryInfo("실손의료보험", "HEALTH", null, "health"),
            )
    }

    data class CategoryInfo(
        val displayName: String,
        val category: String,
        val subcategory: String?,
        val urlGroup: String,
    )

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
                        cat.displayName.contains(filter) || cat.category == filter || cat.subcategory == filter
                    }
                }
            }

        for (categoryInfo in categoriesToCrawl) {
            try {
                val products =
                    crawlerCommon.usePlaywright { browser ->
                        crawlCategory(browser, categoryInfo, batchCollector)
                    }
                allProducts.addAll(products)
                logger.info("생보 [${categoryInfo.displayName}] 크롤링 완료: ${products.size}건")

                if (products.isEmpty()) {
                    logger.warn("생보 [${categoryInfo.displayName}] 수집 건수 0건 — 사이트 구조 변경 확인 필요")
                }
            } catch (e: Exception) {
                allErrors.add(CrawlError(categoryInfo.displayName, null, e.message ?: "", "BROWSER"))
                logger.error("생보 [${categoryInfo.displayName}] 크롤링 실패", e)
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
            parsingWarnings = batchCollector.warnings,
            parsingFailureCount = batchCollector.failureCount,
        )
    }

    suspend fun crawlCategory(
        browser: Browser,
        categoryInfo: CategoryInfo,
        warningCollector: ParsingWarningCollector = ParsingWarningCollector(),
    ): List<ProductRawData> {
        val page = crawlerCommon.createPage(browser)
        return try {
            // 비교공시 페이지 접근
            crawlerCommon.withRetry {
                page.navigate("$BASE_URL/compareDis/prodCompare/${categoryInfo.urlGroup}/listNew.do")
                page.waitForLoadState()
            }

            logger.debug("생보 [${categoryInfo.displayName}] 페이지 접근 완료")

            // 엑셀 다운로드
            val excelPath = downloadExcel(page, categoryInfo)
            if (excelPath != null) {
                // HTML 테이블로 파싱 (Jsoup)
                comparisonDataParser.parseFile(excelPath, categoryInfo.category, categoryInfo.subcategory, warningCollector)
            } else {
                logger.warn("생보 [${categoryInfo.displayName}] 엑셀 다운로드 실패")
                emptyList()
            }
        } finally {
            page.close()
        }
    }

    suspend fun downloadExcel(
        page: Page,
        categoryInfo: CategoryInfo,
    ): Path? =
        withContext(Dispatchers.IO) {
            try {
                val downloadDir = crawlerCommon.buildDatedDownloadDir("life")
                val savePath =
                    downloadDir.resolve(
                        "life_${categoryInfo.category}_${categoryInfo.subcategory ?: "all"}.xls",
                    )

                // 동일 파일이 이미 존재하면 스킵
                if (java.nio.file.Files
                        .exists(savePath) &&
                    java.nio.file.Files
                        .size(savePath) > 0
                ) {
                    logger.info("생보 [${categoryInfo.displayName}] 이미 다운로드됨, 스킵: $savePath")
                    return@withContext savePath
                }

                // 엑셀 다운로드 버튼 클릭
                // 생보협회 실제 셀렉터: fn_excelDown() JS 함수 호출 버튼
                val download =
                    page.waitForDownload {
                        // 다양한 셀렉터 시도 (사이트 구조 변경 대응)
                        val excelButton =
                            page
                                .locator(
                                    "button.btn_excel, " +
                                        "a.btn_excel, " +
                                        "button:has-text('엑셀'), " +
                                        "a:has-text('엑셀'), " +
                                        "button:has-text('Excel'), " +
                                        "a:has-text('Excel'), " +
                                        "[onclick*='excel'], " +
                                        "[onclick*='Excel']",
                                ).first()
                        excelButton.click()
                    }
                download.saveAs(savePath)
                logger.info("생보 [${categoryInfo.displayName}] 엑셀 다운로드 완료: $savePath")
                savePath
            } catch (e: Exception) {
                logger.warn("생보 [${categoryInfo.displayName}] 엑셀 다운로드 실패: ${e.message}")
                null
            }
        }
}
