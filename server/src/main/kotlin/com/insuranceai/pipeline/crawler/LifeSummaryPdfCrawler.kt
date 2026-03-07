package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.pipeline.dto.SummaryDownloadReport
import com.insuranceai.pipeline.dto.SummaryPdfInfo
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

@Component
class LifeSummaryPdfCrawler(
    private val crawlerCommon: CrawlerCommon,
    private val config: PipelineConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val BASE_URL = "https://pub.insure.or.kr"

        // 대분류 → URL 경로 매핑
        val MAJOR_CATEGORIES =
            mapOf(
                "보장성보험" to "/compareDis/prodCompare/assurance/listNew.do",
                "저축성보험" to "/compareDis/prodCompare/saving/list.do",
                "변액보험(보장성)" to "/compareDis/variableInsrn/prodCompare/assurance/listNew.do",
                "변액보험(저축성)" to "/compareDis/variableInsrn/prodCompare/saving/list.do",
            )

        // 보장성보험 소분류 코드
        val ASSURANCE_CATEGORIES =
            mapOf(
                "종신보험" to "024400010001",
                "정기보험" to "024400010002",
                "질병보험" to "024400010003",
                "암보험" to "024400010004",
                "CI보험" to "024400010005",
                "상해보험" to "024400010006",
                "어린이보험" to "024400010007",
                "실손의료보험" to "024400010008",
                "치아보험" to "024400010009",
                "간병/치매보험" to "024400010010",
                "기타" to "024400010011",
            )

        // 연금저축, 실손의료보험은 단일 카테고리로 별도 처리
        val SINGLE_CATEGORIES =
            mapOf(
                "연금저축" to "024400020001",
                "실손의료보험" to "024400010008",
            )

        val FILE_NO_REGEX = Regex("""fn_fileDown\('(\d+)',\s*'(\d+)'\)""")

        // 전체 카테고리 목록 (필터링용)
        val ALL_CATEGORIES =
            ASSURANCE_CATEGORIES.keys +
                setOf("저축성보험", "변액보험(보장성)", "변액보험(저축성)", "연금저축")

        /** 파일명에서 특수문자 제거 */
        fun sanitizeFileName(name: String): String =
            name
                .replace(Regex("""[/\\:*?"<>|]"""), "")
                .replace(Regex("""\s+"""), "_")
                .take(200)

        /** PDF 매직 바이트 확인 (%PDF) */
        fun isPdf(bytes: ByteArray): Boolean = bytes.size >= 4 && String(bytes, 0, 4) == "%PDF"
    }

    /** 모든 카테고리에서 SummaryPdfInfo 수집 */
    suspend fun collectAll(categoryFilter: List<String> = emptyList()): List<SummaryPdfInfo> {
        val allInfos = mutableListOf<SummaryPdfInfo>()

        // 보장성보험 소분류 순회
        val assuranceCategories =
            if (categoryFilter.isEmpty()) {
                ASSURANCE_CATEGORIES
            } else {
                ASSURANCE_CATEGORIES.filterKeys { it in categoryFilter }
            }

        if (assuranceCategories.isNotEmpty()) {
            crawlerCommon.usePlaywright { browser ->
                for ((category, code) in assuranceCategories) {
                    try {
                        val infos = collectCategory(browser, category, code, "보장성보험")
                        allInfos.addAll(infos)
                        logger.info("[보장성보험/$category] 수집 완료: ${infos.size}건")
                    } catch (e: Exception) {
                        logger.error("[보장성보험/$category] 수집 실패: ${e.message}", e)
                    }
                    crawlerCommon.randomDelay()
                }
            }
        }

        // 저축성/변액 대분류 순회 (소분류 코드를 페이지에서 동적으로 수집)
        val otherMajors =
            MAJOR_CATEGORIES.filter { (key, _) ->
                key != "보장성보험" && (categoryFilter.isEmpty() || key in categoryFilter)
            }

        for ((majorCategory, urlPath) in otherMajors) {
            try {
                crawlerCommon.usePlaywright { browser ->
                    val infos = collectMajorCategory(browser, majorCategory, urlPath)
                    allInfos.addAll(infos)
                    logger.info("[$majorCategory] 수집 완료: ${infos.size}건")
                }
            } catch (e: Exception) {
                logger.error("[$majorCategory] 수집 실패: ${e.message}", e)
            }
            crawlerCommon.randomDelay()
        }

        logger.info("전체 수집 완료: ${allInfos.size}건")
        return allInfos
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

        val httpClient =
            HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

        for ((index, info) in infos.withIndex()) {
            try {
                val savePath = buildSavePath(info)

                if (!force && Files.exists(savePath) && Files.size(savePath) > 0) {
                    skipped++
                    continue
                }

                val success = downloadPdf(httpClient, info, savePath)
                if (success) {
                    downloaded++
                    categoryStats.merge(info.category, 1) { a, b -> a + b }
                } else {
                    failed++
                    errors.add("PDF 검증 실패: ${info.companyName}_${info.productName} (fileNo=${info.fileNo})")
                }

                if ((index + 1) % 10 == 0) {
                    logger.info("다운로드 진행: ${index + 1}/${infos.size} (성공=$downloaded, 스킵=$skipped, 실패=$failed)")
                }

                crawlerCommon.randomDelay()
            } catch (e: Exception) {
                failed++
                errors.add("다운로드 실패: ${info.companyName}_${info.productName}: ${e.message}")
                logger.warn("다운로드 실패: ${info.companyName}_${info.productName}", e)
            }
        }

        logger.info("다운로드 완료: 총=${infos.size}, 성공=$downloaded, 스킵=$skipped, 실패=$failed")
        return SummaryDownloadReport(
            totalCollected = infos.size,
            downloaded = downloaded,
            skipped = skipped,
            failed = failed,
            errors = errors,
            categoryStats = categoryStats,
        )
    }

    /** 보장성보험 한 소분류 카테고리의 모든 페이지 순회 */
    internal suspend fun collectCategory(
        browser: Browser,
        category: String,
        code: String,
        majorCategory: String,
    ): List<SummaryPdfInfo> {
        val allInfos = mutableListOf<SummaryPdfInfo>()
        val page = crawlerCommon.createPage(browser)

        try {
            val urlPath = MAJOR_CATEGORIES[majorCategory] ?: MAJOR_CATEGORIES["보장성보험"]!!
            crawlerCommon.withRetry {
                page.navigate("$BASE_URL$urlPath")
                page.waitForLoadState()
            }

            // 카테고리 라디오 버튼 선택 (label > span이 input을 가리므로 force 클릭)
            val radioSelector = "input[name=search_prodGroup][value='$code']"
            page.locator(radioSelector).click(Locator.ClickOptions().setForce(true))
            crawlerCommon.randomDelay()

            // 조회 버튼 클릭
            clickSearchButton(page)
            page.waitForLoadState()
            crawlerCommon.randomDelay()

            // 페이지네이션 순회
            var pageNo = 1
            while (true) {
                val infos = extractFromPage(page, category, majorCategory)
                allInfos.addAll(infos)

                val hasNext = hasNextPage(page, pageNo)
                if (!hasNext) break

                pageNo++
                page.evaluate("fn_page($pageNo)")
                page.waitForLoadState()
                crawlerCommon.randomDelay()
            }
        } finally {
            page.close()
        }

        return allInfos
    }

    /** 저축성/변액 대분류 페이지에서 소분류를 동적으로 수집하고 순회 */
    private suspend fun collectMajorCategory(
        browser: Browser,
        majorCategory: String,
        urlPath: String,
    ): List<SummaryPdfInfo> {
        val allInfos = mutableListOf<SummaryPdfInfo>()
        val page = crawlerCommon.createPage(browser)

        try {
            crawlerCommon.withRetry {
                page.navigate("$BASE_URL$urlPath")
                page.waitForLoadState()
            }

            // 소분류 라디오 버튼 코드 수집
            val subCategories = extractSubCategories(page)

            if (subCategories.isEmpty()) {
                // 소분류 없이 바로 상품 목록 표시되는 경우
                val infos = collectPagesFromCurrentView(page, majorCategory, majorCategory)
                allInfos.addAll(infos)
            } else {
                for ((subName, subCode) in subCategories) {
                    try {
                        val radioSelector = "input[name=search_prodGroup][value='$subCode']"
                        page.locator(radioSelector).click(Locator.ClickOptions().setForce(true))
                        crawlerCommon.randomDelay()

                        clickSearchButton(page)
                        page.waitForLoadState()
                        crawlerCommon.randomDelay()

                        val infos = collectPagesFromCurrentView(page, subName, majorCategory)
                        allInfos.addAll(infos)
                        logger.info("[$majorCategory/$subName] 수집: ${infos.size}건")
                    } catch (e: Exception) {
                        logger.warn("[$majorCategory/$subName] 수집 실패: ${e.message}")
                    }
                    crawlerCommon.randomDelay()
                }
            }
        } finally {
            page.close()
        }

        return allInfos
    }

    /** 현재 페이지 뷰에서 모든 페이지네이션 순회 */
    private suspend fun collectPagesFromCurrentView(
        page: Page,
        category: String,
        majorCategory: String,
    ): List<SummaryPdfInfo> {
        val allInfos = mutableListOf<SummaryPdfInfo>()
        var pageNo = 1

        while (true) {
            val infos = extractFromPage(page, category, majorCategory)
            allInfos.addAll(infos)

            val hasNext = hasNextPage(page, pageNo)
            if (!hasNext) break

            pageNo++
            page.evaluate("fn_page($pageNo)")
            page.waitForLoadState()
            crawlerCommon.randomDelay()
        }

        return allInfos
    }

    /** 한 페이지에서 Download 버튼 정보 추출 */
    internal fun extractFromPage(
        page: Page,
        category: String,
        majorCategory: String,
    ): List<SummaryPdfInfo> {
        @Suppress("UNCHECKED_CAST")
        val jsResult =
            page.evaluate(
                """() => {
                const buttons = document.querySelectorAll('button.list_btn_down');
                return Array.from(buttons).map(btn => {
                    const onclick = btn.getAttribute('onclick') || '';
                    const row = btn.closest('tr');
                    const cells = row ? row.querySelectorAll('td') : [];
                    return {
                        onclick: onclick,
                        company: cells.length > 1 ? cells[1].textContent.trim() : '',
                        product: cells.length > 2 ? cells[2].textContent.trim() : ''
                    };
                });
            }""",
            ) as? List<Map<String, Any?>> ?: emptyList()

        return jsResult.mapNotNull { item ->
            val onclick = item["onclick"]?.toString() ?: return@mapNotNull null
            val match = FILE_NO_REGEX.find(onclick) ?: return@mapNotNull null
            val company = item["company"]?.toString()?.trim() ?: return@mapNotNull null
            val product = item["product"]?.toString()?.trim() ?: return@mapNotNull null

            if (company.isBlank() || product.isBlank()) return@mapNotNull null

            SummaryPdfInfo(
                fileNo = match.groupValues[1],
                seq = match.groupValues[2],
                companyName = company,
                productName = product,
                category = category,
                majorCategory = majorCategory,
            )
        }
    }

    /** 페이지에서 소분류 라디오 버튼 목록 추출 */
    private fun extractSubCategories(page: Page): List<Pair<String, String>> {
        @Suppress("UNCHECKED_CAST")
        val jsResult =
            page.evaluate(
                """() => {
                const radios = document.querySelectorAll('input[name=search_prodGroup]');
                return Array.from(radios).map(radio => {
                    const label = radio.parentElement ? radio.parentElement.textContent.trim() : '';
                    return { code: radio.value, label: label };
                });
            }""",
            ) as? List<Map<String, Any?>> ?: emptyList()

        return jsResult.mapNotNull { item ->
            val code = item["code"]?.toString() ?: return@mapNotNull null
            val label = item["label"]?.toString()?.trim() ?: return@mapNotNull null
            if (code.isBlank() || label.isBlank()) return@mapNotNull null
            label to code
        }
    }

    /** 다음 페이지 존재 여부 확인 */
    private fun hasNextPage(
        page: Page,
        currentPage: Int,
    ): Boolean {
        val result =
            page.evaluate(
                """(currentPage) => {
                // 페이지네이션 영역에서 다음 페이지 링크 확인
                const paging = document.querySelector('.paginate, .paging, .pagination, .page_wrap');
                if (!paging) return false;
                const links = paging.querySelectorAll('a');
                for (const link of links) {
                    const onclick = link.getAttribute('onclick') || link.getAttribute('href') || '';
                    if (onclick.includes('fn_page(' + (currentPage + 1) + ')')) return true;
                }
                return false;
            }""",
                currentPage,
            )
        return result as? Boolean ?: false
    }

    /** 조회 버튼 클릭 (fn_search(1) JS 호출) */
    private fun clickSearchButton(page: Page) {
        page.evaluate("fn_search(1)")
    }

    /** PDF 1건 다운로드 */
    private suspend fun downloadPdf(
        httpClient: HttpClient,
        info: SummaryPdfInfo,
        savePath: Path,
    ): Boolean =
        withContext(Dispatchers.IO) {
            crawlerCommon.withRetry(maxRetries = 3) {
                Files.createDirectories(savePath.parent)

                val url = "$BASE_URL/FileDown.do?fileNo=${info.fileNo}&seq=${info.seq}"
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", config.crawl.userAgent)
                        .GET()
                        .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                val body = response.body()

                // PDF 매직 바이트 검증
                if (body.size < 4 || !isPdf(body)) {
                    logger.warn("PDF 아님: ${info.companyName}_${info.productName} (fileNo=${info.fileNo}, size=${body.size})")
                    return@withRetry false
                }

                Files.write(savePath, body)
                true
            }
        }

    /** 저장 경로 생성 (날짜별 폴더) */
    internal fun buildSavePath(info: SummaryPdfInfo): Path {
        val categoryDir = sanitizeFileName(info.category)
        val fileName = "${sanitizeFileName(info.companyName)}_${sanitizeFileName(info.productName)}_${info.fileNo}.pdf"
        return crawlerCommon.buildDatedDownloadDir("life", categoryDir).resolve(fileName)
    }
}
