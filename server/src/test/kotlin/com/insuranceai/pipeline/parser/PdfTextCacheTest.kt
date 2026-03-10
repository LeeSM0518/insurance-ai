package com.insuranceai.pipeline.parser

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfTextCacheTest {
    private val pdfTextExtractor = mockk<PdfTextExtractor>()
    private val visionParser = mockk<VisionParser>()
    private val pdfStructureExtractor = mockk<PdfStructureExtractor>()
    private val service = PdfParsingService(pdfTextExtractor, visionParser, pdfStructureExtractor)

    @TempDir
    lateinit var tempDir: File

    // -- saveToTextFile / loadFromTextFile 라운드트립 --

    @Test
    fun `saveToTextFile과 loadFromTextFile 라운드트립이 정확하다`() {
        val pages =
            listOf(
                PageExtractionResult(1, "첫 번째 페이지 내용", false, dummyMetrics()),
                PageExtractionResult(2, "두 번째 페이지 내용", true, dummyMetrics()),
                PageExtractionResult(3, "세 번째 페이지\n여러 줄 포함", false, dummyMetrics()),
            )

        val file = File(tempDir, "test_cache.txt")
        service.saveToTextFile(pages, file)

        assertTrue(file.exists())
        val loaded = service.loadFromTextFile(file)

        assertEquals(3, loaded.size)
        assertEquals(1, loaded[0].pageNumber)
        assertEquals("첫 번째 페이지 내용", loaded[0].text)
        assertEquals(2, loaded[1].pageNumber)
        assertEquals("두 번째 페이지 내용", loaded[1].text)
        assertEquals(3, loaded[2].pageNumber)
        assertEquals("세 번째 페이지\n여러 줄 포함", loaded[2].text)
        // 캐시 로드 시 OCR은 항상 false
        loaded.forEach { assertEquals(false, it.needsOcr) }
    }

    @Test
    fun `loadFromTextFile은 후행 빈 줄이 포함된 파일을 정상 파싱한다`() {
        val content = "--- PAGE 1 ---\n내용 1\n\n\n--- PAGE 2 ---\n내용 2\n\n"
        val file = File(tempDir, "trailing_newline.txt")
        file.writeText(content, Charsets.UTF_8)

        val loaded = service.loadFromTextFile(file)

        assertEquals(2, loaded.size)
        assertEquals("내용 1", loaded[0].text)
        assertEquals("내용 2", loaded[1].text)
    }

    @Test
    fun `loadFromTextFile은 본문에 PAGE 유사 문자열이 있어도 정상 파싱한다`() {
        val content = "--- PAGE 1 ---\n본문에 PAGE 10 관련 설명\n그리고 --- PAGE 같은 텍스트\n\n--- PAGE 2 ---\n두 번째 페이지"
        val file = File(tempDir, "page_like_text.txt")
        file.writeText(content, Charsets.UTF_8)

        val loaded = service.loadFromTextFile(file)

        assertEquals(2, loaded.size)
        assertEquals("본문에 PAGE 10 관련 설명\n그리고 --- PAGE 같은 텍스트", loaded[0].text)
        assertEquals("두 번째 페이지", loaded[1].text)
    }

    // -- resolveTextCachePath --

    @Test
    fun `resolveTextCachePath는 PDF와 같은 디렉토리에 extracted txt를 반환한다`() {
        val pdfFile = File(tempDir, "summary.pdf")
        val cachePath = service.resolveTextCachePath(pdfFile)
        assertEquals(File(tempDir, "extracted.txt"), cachePath)
    }

    // -- parse 캐시 동작 --

    @Test
    fun `캐시 파일이 없으면 Phase 1~2를 실행하고 캐시를 생성한다`() =
        runTest {
            val pdfFile = File(tempDir, "summary.pdf")
            pdfFile.writeText("dummy pdf content")

            val pages =
                listOf(
                    PageExtractionResult(1, "추출된 텍스트", false, dummyMetrics()),
                )
            val extraction = PdfExtractionResult(pages, 0.0f, 100)
            coEvery { pdfTextExtractor.extract(pdfFile) } returns extraction

            val structuredData = PdfStructuredData(overallConfidence = 0.9f)
            coEvery { pdfStructureExtractor.extract(any(), any(), any()) } returns structuredData
            coEvery { pdfStructureExtractor.factCheck(any(), any()) } returns emptyList()

            val result = service.parse(pdfFile, UUID.randomUUID(), "테스트보험", "테스트상품")

            // Phase 1이 호출됨
            coVerify(exactly = 1) { pdfTextExtractor.extract(pdfFile) }

            // 캐시 파일이 생성됨
            val cacheFile = File(tempDir, "extracted.txt")
            assertTrue(cacheFile.exists())
            assertTrue(cacheFile.readText().contains("--- PAGE 1 ---"))

            // extractedTextPath가 상대 경로로 저장됨
            val baseDir =
                java.nio.file.Path
                    .of("")
                    .toAbsolutePath()
            val expectedPath = baseDir.relativize(cacheFile.toPath()).toString()
            assertEquals(expectedPath, result.dataSource.extractedTextPath)
        }

    @Test
    fun `캐시 파일이 있으면 Phase 1~2를 스킵한다`() =
        runTest {
            val pdfFile = File(tempDir, "summary.pdf")
            pdfFile.writeText("dummy pdf content")

            // 캐시 파일 미리 생성 (PDF보다 최신)
            val cacheFile = File(tempDir, "extracted.txt")
            cacheFile.writeText("--- PAGE 1 ---\n캐시된 텍스트", Charsets.UTF_8)
            cacheFile.setLastModified(System.currentTimeMillis() + 1000)

            val structuredData = PdfStructuredData(overallConfidence = 0.85f)
            coEvery { pdfStructureExtractor.extract(any(), any(), any()) } returns structuredData
            coEvery { pdfStructureExtractor.factCheck(any(), any()) } returns emptyList()

            service.parse(pdfFile, UUID.randomUUID(), "테스트보험", "테스트상품")

            // Phase 1이 호출되지 않음
            coVerify(exactly = 0) { pdfTextExtractor.extract(any()) }
        }

    @Test
    fun `PDF가 캐시보다 최신이면 캐시를 재생성한다`() =
        runTest {
            val pdfFile = File(tempDir, "summary.pdf")
            pdfFile.writeText("updated pdf content")

            // 캐시 파일이 PDF보다 오래됨
            val cacheFile = File(tempDir, "extracted.txt")
            cacheFile.writeText("--- PAGE 1 ---\n오래된 캐시", Charsets.UTF_8)
            cacheFile.setLastModified(pdfFile.lastModified() - 1000)

            val pages =
                listOf(
                    PageExtractionResult(1, "새로 추출된 텍스트", false, dummyMetrics()),
                )
            val extraction = PdfExtractionResult(pages, 0.0f, 100)
            coEvery { pdfTextExtractor.extract(pdfFile) } returns extraction

            val structuredData = PdfStructuredData(overallConfidence = 0.9f)
            coEvery { pdfStructureExtractor.extract(any(), any(), any()) } returns structuredData
            coEvery { pdfStructureExtractor.factCheck(any(), any()) } returns emptyList()

            service.parse(pdfFile, UUID.randomUUID(), "테스트보험", "테스트상품")

            // Phase 1이 호출됨 (캐시 무효화)
            coVerify(exactly = 1) { pdfTextExtractor.extract(pdfFile) }

            // 캐시 파일이 새로운 내용으로 업데이트됨
            assertTrue(cacheFile.readText().contains("새로 추출된 텍스트"))
        }

    @Test
    fun `forceExtract=true이면 캐시를 무시하고 재추출한다`() =
        runTest {
            val pdfFile = File(tempDir, "summary.pdf")
            pdfFile.writeText("dummy pdf content")

            // 유효한 캐시 파일이 있음
            val cacheFile = File(tempDir, "extracted.txt")
            cacheFile.writeText("--- PAGE 1 ---\n기존 캐시 텍스트", Charsets.UTF_8)
            cacheFile.setLastModified(System.currentTimeMillis() + 1000)

            val pages =
                listOf(
                    PageExtractionResult(1, "강제 재추출된 텍스트", false, dummyMetrics()),
                )
            val extraction = PdfExtractionResult(pages, 0.0f, 100)
            coEvery { pdfTextExtractor.extract(pdfFile) } returns extraction

            val structuredData = PdfStructuredData(overallConfidence = 0.9f)
            coEvery { pdfStructureExtractor.extract(any(), any(), any()) } returns structuredData
            coEvery { pdfStructureExtractor.factCheck(any(), any()) } returns emptyList()

            service.parse(pdfFile, UUID.randomUUID(), "테스트보험", "테스트상품", forceExtract = true)

            // forceExtract이므로 Phase 1이 호출됨
            coVerify(exactly = 1) { pdfTextExtractor.extract(pdfFile) }

            // 캐시 파일이 새로운 내용으로 업데이트됨
            assertTrue(cacheFile.readText().contains("강제 재추출된 텍스트"))
        }

    private fun dummyMetrics() =
        PageQualityMetrics(
            charCount = 100,
            koreanRatio = 0.8f,
            avgLineLength = 30f,
            uniqueCharCount = 50,
        )
}
