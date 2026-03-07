package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.SummaryDownloadReport
import com.insuranceai.pipeline.dto.SummaryPdfInfo
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * 손해보험협회 상품요약서 PDF 크롤러
 *
 * 비교공시 팝업 페이지의 DOM에서 요약서 정보(summary_seq)를 추출하고,
 * /file/download/{summary_seq}.do 경로로 PDF 다운로드.
 */
@Component
class NonLifeSummaryPdfCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BASE_URL = "https://kpub.knia.or.kr"

        /** 파일명에서 특수문자 제거 */
        fun sanitizeFileName(name: String): String =
            name
                .replace(Regex("""[/\\:*?"<>|]"""), "")
                .replace(Regex("""\s+"""), "_")
                .take(200)
    }

    /** 모든 카테고리에서 SummaryPdfInfo 수집 */
    suspend fun collectAll(categoryFilter: List<String> = emptyList()): List<SummaryPdfInfo> {
        val allInfos = mutableListOf<SummaryPdfInfo>()

        val categoriesToCrawl =
            if (categoryFilter.isEmpty()) {
                NonLifeInsuranceCrawler.CATEGORIES
            } else {
                NonLifeInsuranceCrawler.CATEGORIES.filter { cat ->
                    categoryFilter.any { filter ->
                        cat.categoryName.contains(filter) ||
                            cat.tptyCode == filter ||
                            cat.tabName == filter
                    }
                }
            }

        crawlerCommon.usePlaywright { browser ->
            for (category in categoriesToCrawl) {
                try {
                    val infos = collectCategory(browser, category)
                    allInfos.addAll(infos)
                    logger.info("손보 [${category.tabName}/${category.categoryName}] 요약서 수집: ${infos.size}건")
                } catch (e: Exception) {
                    logger.error("손보 [${category.tabName}/${category.categoryName}] 요약서 수집 실패: ${e.message}", e)
                }
                crawlerCommon.randomDelay()
            }
        }

        logger.info("손보 요약서 전체 수집 완료: ${allInfos.size}건")
        return allInfos
    }

    /** 한 카테고리 페이지에서 요약서 정보 추출 */
    internal suspend fun collectCategory(
        browser: Browser,
        category: NonLifeInsuranceCrawler.NonLifeCategory,
    ): List<SummaryPdfInfo> {
        val page = crawlerCommon.createPage(browser)
        return try {
            val url = NonLifeInsuranceCrawler.buildCategoryUrl(category)
            crawlerCommon.withRetry {
                page.navigate(url)
                page.waitForLoadState()
            }

            extractSummaryInfos(page, category)
        } finally {
            page.close()
        }
    }

    /**
     * DOM에서 요약서 정보 추출
     *
     * #list tbody.tbodyClass 순회하여 회사명, 상품명, summary_seq 추출.
     * 페이지네이션 없음 — 모든 상품이 한 페이지에 로딩됨.
     */
    internal fun extractSummaryInfos(
        page: Page,
        category: NonLifeInsuranceCrawler.NonLifeCategory,
    ): List<SummaryPdfInfo> {
        @Suppress("UNCHECKED_CAST")
        val jsResult =
            page.evaluate(
                """() => {
                const tbodies = document.querySelectorAll('#list tbody.tbodyClass');
                return Array.from(tbodies).map(tb => {
                    const firstRow = tb.querySelector('tr');
                    const tds = firstRow ? firstRow.querySelectorAll('td') : [];
                    const summarySeq = tb.querySelector('input[name=summary_seq]');
                    const tpCode = tb.querySelector('input[name=tp_code]');
                    return {
                        company: tds.length > 1 ? tds[1].textContent.trim() : '',
                        product: tds.length > 2 ? tds[2].textContent.trim() : '',
                        summarySeq: summarySeq ? summarySeq.value : null,
                        tpCode: tpCode ? tpCode.value : null
                    };
                }).filter(item => item.summarySeq && item.company && item.product);
            }""",
            ) as? List<Map<String, Any?>> ?: emptyList()

        return jsResult.mapNotNull { item ->
            val company = item["company"]?.toString()?.trim() ?: return@mapNotNull null
            val product = item["product"]?.toString()?.trim() ?: return@mapNotNull null
            val summarySeq = item["summarySeq"]?.toString() ?: return@mapNotNull null

            if (company.isBlank() || product.isBlank() || summarySeq.isBlank()) return@mapNotNull null

            SummaryPdfInfo(
                fileNo = summarySeq,
                seq = "",
                companyName = company,
                productName = product,
                category = category.categoryName,
                majorCategory = category.tabName,
            )
        }
    }

    /** 수집된 정보로 PDF 일괄 다운로드 */
    suspend fun downloadAll(
        infos: List<SummaryPdfInfo>,
        force: Boolean = false,
    ): SummaryDownloadReport {
        var downloaded = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()
        val categoryStats = mutableMapOf<String, Int>()

        crawlerCommon.usePlaywright { browser ->
            val page = crawlerCommon.createPage(browser)
            try {
                // 세션 초기화를 위해 아무 페이지 접근
                page.navigate("$BASE_URL/popup/disclosurePopup.do?tabType=1&tptyCode=PB11&detailYn=Y")
                page.waitForLoadState()

                for ((index, info) in infos.withIndex()) {
                    try {
                        val savePath = buildSavePath(info)

                        if (!force && Files.exists(savePath) && Files.size(savePath) > 0) {
                            skipped++
                            continue
                        }

                        val url = "$BASE_URL/file/download/${info.fileNo}.do"
                        crawlerCommon.downloadFile(page, url, savePath)
                        downloaded++
                        categoryStats.merge(info.category, 1) { a, b -> a + b }

                        if ((index + 1) % 10 == 0) {
                            logger.info(
                                "손보 PDF 다운로드 진행: ${index + 1}/${infos.size} " +
                                    "(성공=$downloaded, 스킵=$skipped, 실패=$failed)",
                            )
                        }

                        crawlerCommon.randomDelay()
                    } catch (e: Exception) {
                        failed++
                        errors.add("다운로드 실패: ${info.companyName}_${info.productName}: ${e.message}")
                        logger.warn("손보 PDF 다운로드 실패: ${info.companyName}_${info.productName}", e)
                    }
                }
            } finally {
                page.close()
            }
        }

        logger.info("손보 PDF 다운로드 완료: 총=${infos.size}, 성공=$downloaded, 스킵=$skipped, 실패=$failed")
        return SummaryDownloadReport(
            totalCollected = infos.size,
            downloaded = downloaded,
            skipped = skipped,
            failed = failed,
            errors = errors,
            categoryStats = categoryStats,
        )
    }

    /** 저장 경로 생성 (날짜별 폴더) */
    internal fun buildSavePath(info: SummaryPdfInfo): Path {
        val categoryDir = sanitizeFileName(info.category)
        val fileName = "${sanitizeFileName(info.companyName)}_${sanitizeFileName(info.productName)}_${info.fileNo}.pdf"
        return crawlerCommon.buildDatedDownloadDir("non-life", categoryDir).resolve(fileName)
    }
}
