package com.insuranceai.pipeline.crawler

import com.insuranceai.pipeline.config.PipelineConfig
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

@Component
class CrawlerCommon(
    private val config: PipelineConfig,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun <T> usePlaywright(block: suspend (Browser) -> T): T =
        withContext(Dispatchers.IO) {
            Playwright.create().use { playwright ->
                playwright
                    .chromium()
                    .launch(
                        BrowserType.LaunchOptions().setHeadless(config.crawl.headless),
                    ).use { browser ->
                        block(browser)
                    }
            }
        }

    fun createPage(browser: Browser): Page {
        val context =
            browser.newContext(
                Browser
                    .NewContextOptions()
                    .setUserAgent(config.crawl.userAgent),
            )
        return context.newPage()
    }

    suspend fun randomDelay() {
        val delayMs = (config.crawl.delayMinMs..config.crawl.delayMaxMs).random()
        delay(delayMs)
    }

    suspend fun <T> withRetry(
        maxRetries: Int = config.crawl.maxRetries,
        block: suspend () -> T,
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val backoffMs = (1000L * (1 shl attempt)).coerceAtMost(30_000L)
                logger.warn("재시도 ${attempt + 1}/$maxRetries (${backoffMs}ms 후): ${e.message}")
                delay(backoffMs)
            }
        }
        throw lastException ?: IllegalStateException("재시도 실패")
    }

    fun calculateChecksum(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content).joinToString("") { "%02x".format(it) }
    }

    suspend fun downloadFile(
        page: Page,
        url: String,
        savePath: Path,
    ): Path =
        withContext(Dispatchers.IO) {
            Files.createDirectories(savePath.parent)
            val response = page.context().request().get(url)
            Files.write(savePath, response.body())
            savePath
        }

    /** @deprecated buildCategoryDir / buildExcelPath / buildProductDir 사용 */
    @Deprecated("새 경로 빌더 사용", ReplaceWith("buildCategoryDir(type, category)"))
    fun buildDatedDownloadDir(vararg subPaths: String): Path {
        val date = LocalDate.now().toString()
        val base = Path.of(config.crawl.downloadDir, *subPaths, date)
        Files.createDirectories(base)
        return base
    }

    /** 파일/디렉토리명에서 특수문자 제거 */
    fun sanitize(name: String): String =
        name
            .replace(Regex("""[/\\:*?"<>|]"""), "")
            .replace(Regex("""\s+"""), "_")
            .take(200)

    /** 카테고리 기반 경로: data/summaries/{type}/{category}/{yyyy-MM}/ */
    fun buildCategoryDir(
        type: String,
        category: String,
    ): Path {
        val date = YearMonth.now().toString()
        val base = Path.of(config.crawl.downloadDir, type, sanitize(category), date)
        Files.createDirectories(base)
        return base
    }

    /** 엑셀 경로: .../{category}/{yyyy-MM}/excel/comparison.xls */
    fun buildExcelPath(
        type: String,
        category: String,
    ): Path {
        val dir = buildCategoryDir(type, category).resolve("excel")
        Files.createDirectories(dir)
        return dir.resolve("comparison.xls")
    }

    /** 상품 디렉토리 경로: .../{category}/{yyyy-MM}/{회사}_{상품}/ */
    fun buildProductDir(
        type: String,
        category: String,
        company: String,
        product: String,
    ): Path {
        val dirName = "${sanitize(company)}_${sanitize(product)}".take(200)
        val dir = buildCategoryDir(type, category).resolve(dirName)
        Files.createDirectories(dir)
        return dir
    }

    /** PDF 경로: .../{회사}_{상품}/summary.pdf */
    fun buildSummaryPdfPath(
        type: String,
        category: String,
        company: String,
        product: String,
    ): Path = buildProductDir(type, category, company, product).resolve("summary.pdf")

    /** 갱신보험료 PDF 경로: .../{회사}_{상품}/renewal-premium.pdf */
    fun buildRenewalPremiumPdfPath(
        type: String,
        category: String,
        company: String,
        product: String,
    ): Path = buildProductDir(type, category, company, product).resolve("renewal-premium.pdf")

    /** 텍스트 캐시 경로: .../{회사}_{상품}/extracted.txt */
    fun buildExtractedTextPath(
        type: String,
        category: String,
        company: String,
        product: String,
    ): Path = buildProductDir(type, category, company, product).resolve("extracted.txt")

    fun normalizeCompanyName(name: String): String = name.replace(Regex("\\(주\\)|㈜|주식회사|보험(?=\\s*$)"), "").trim()

    private val companyAliasMap =
        mapOf(
            // 생보 — 비교공시 표기 → DB 저장명
            "삼성생명보험" to "삼성생명",
            "NH농협생명보험" to "NH농협생명",
            "한화생명보험" to "한화생명",
            "교보생명보험" to "교보생명",
            "신한라이프생명보험" to "신한라이프생명",
            "DB생명보험" to "DB생명",
            "KB라이프생명보험" to "KB라이프생명",
            "메트라이프생명보험" to "메트라이프생명",
            "처브라이프생명보험" to "처브라이프생명",
            "교보라이프플래닛생명보험" to "교보라이프플래닛생명",
            "IBK연금보험" to "IBK연금보험",
            // 손보
            "메리츠화재해상보험" to "메리츠화재",
            "메리츠화재" to "메리츠화재",
            "삼성화재해상보험" to "삼성화재",
            "삼성화재" to "삼성화재",
            "현대해상화재보험" to "현대해상",
            "현대해상" to "현대해상",
            "KB손해보험" to "KB손해보험",
            "KB손보" to "KB손해보험",
            "DB손해보험" to "DB손해보험",
            "DB손보" to "DB손해보험",
            "한화손해보험" to "한화손해보험",
            "한화손보" to "한화손해보험",
            "하나손해보험" to "하나손해보험",
            "하나손보" to "하나손해보험",
            "라이나손해보험" to "라이나손해보험",
            "라이나손보(에이스손보)" to "라이나손해보험",
            "신한EZ손해보험" to "신한EZ손해보험",
            "농협손해보험" to "농협손해보험",
            "농협손보" to "농협손해보험",
            "NH농협손해보험" to "농협손해보험",
        )

    suspend fun matchCompany(crawledName: String): InsuranceCompanyEntity? {
        val normalized = normalizeCompanyName(crawledName)
        val aliased = companyAliasMap[crawledName] ?: companyAliasMap[normalized] ?: normalized

        val companies = insuranceCompanyRepository.findByIsActiveTrue().toList()
        val matched =
            companies.firstOrNull { company ->
                company.name == aliased ||
                    company.name == normalized ||
                    company.name == crawledName ||
                    normalizeCompanyName(company.name) == normalized
            }

        if (matched == null) {
            logger.warn("보험사 매칭 실패: '$crawledName' (정규화: '$normalized', alias: '$aliased')")
        }
        return matched
    }
}
