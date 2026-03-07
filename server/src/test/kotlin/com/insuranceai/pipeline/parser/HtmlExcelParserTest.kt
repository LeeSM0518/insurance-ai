package com.insuranceai.pipeline.parser

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlExcelParserTest {
    @TempDir
    lateinit var tempDir: Path

    private fun createHtmlFile(content: String): Path {
        val file = tempDir.resolve("test.xls")
        file.toFile().writeText(content, Charsets.UTF_8)
        return file
    }

    @Nested
    inner class Parse {
        @Test
        fun `단순 HTML 테이블 파싱`() {
            val html =
                """
                <table>
                    <thead>
                        <tr><th>보험회사명</th><th>상품명</th><th>보험료</th></tr>
                    </thead>
                    <tbody>
                        <tr><td>삼성생명</td><td>종신보험A</td><td>50000</td></tr>
                        <tr><td>한화생명</td><td>종신보험B</td><td>60000</td></tr>
                    </tbody>
                </table>
                """.trimIndent()

            val file = createHtmlFile(html)
            val result = HtmlExcelParser.parse(file)

            assertEquals(3, result.headers.size)
            assertEquals("보험회사명", result.headers[0])
            assertEquals("상품명", result.headers[1])
            assertEquals("보험료", result.headers[2])
            assertEquals(2, result.rows.size)
            assertEquals("삼성생명", result.rows[0][0])
            assertEquals("종신보험A", result.rows[0][1])
        }

        @Test
        fun `멀티 레벨 헤더 flatten`() {
            val html =
                """
                <table>
                    <thead>
                        <tr><th rowspan="2">보험회사명</th><th colspan="2">보험료</th></tr>
                        <tr><th>남자</th><th>여자</th></tr>
                    </thead>
                    <tbody>
                        <tr><td>삼성생명</td><td>50000</td><td>45000</td></tr>
                    </tbody>
                </table>
                """.trimIndent()

            val file = createHtmlFile(html)
            val result = HtmlExcelParser.parse(file)

            assertEquals(3, result.headers.size)
            assertEquals("보험회사명", result.headers[0])
            assertEquals("보험료_남자", result.headers[1])
            assertEquals("보험료_여자", result.headers[2])
        }

        @Test
        fun `3단계 헤더 flatten`() {
            val html =
                """
                <table>
                    <thead>
                        <tr><th rowspan="3">보험회사명</th><th colspan="4">보장내용 및 보험료</th></tr>
                        <tr><th colspan="2">보장내용</th><th colspan="2">보험료</th></tr>
                        <tr><th>급부명칭</th><th>지급사유</th><th>남자</th><th>여자</th></tr>
                    </thead>
                    <tbody>
                        <tr><td>삼성생명</td><td>사망</td><td>사망 시</td><td>50000</td><td>45000</td></tr>
                    </tbody>
                </table>
                """.trimIndent()

            val file = createHtmlFile(html)
            val result = HtmlExcelParser.parse(file)

            assertEquals(5, result.headers.size)
            assertEquals("보험회사명", result.headers[0])
            // 연속 중복 제거: "보장내용 및 보험료_보장내용_급부명칭"
            assert(result.headers[1].contains("급부명칭"))
            assert(result.headers[3].contains("남자"))
        }

        @Test
        fun `rowspan 데이터 행 처리`() {
            val html =
                """
                <table>
                    <thead>
                        <tr><th>보험회사명</th><th>상품명</th><th>구분</th></tr>
                    </thead>
                    <tbody>
                        <tr><td rowspan="2">삼성생명</td><td rowspan="2">종신A</td><td>주계약</td></tr>
                        <tr><td>특약</td></tr>
                    </tbody>
                </table>
                """.trimIndent()

            val file = createHtmlFile(html)
            val result = HtmlExcelParser.parse(file)

            assertEquals(2, result.rows.size)
            assertEquals("삼성생명", result.rows[0][0])
            assertEquals("삼성생명", result.rows[1][0]) // rowspan으로 유지
            assertEquals("주계약", result.rows[0][2])
            assertEquals("특약", result.rows[1][2])
        }
    }

    @Nested
    inner class ForwardFill {
        @Test
        fun `빈 값을 이전 값으로 채움`() {
            val rows =
                listOf(
                    listOf("삼성생명", "종신A", "주계약"),
                    listOf("", "", "특약"),
                    listOf("한화생명", "종신B", "주계약"),
                )

            val filled = HtmlExcelParser.forwardFill(rows, listOf(0, 1))

            assertEquals("삼성생명", filled[0][0])
            assertEquals("삼성생명", filled[1][0]) // forward-fill
            assertEquals("종신A", filled[1][1]) // forward-fill
            assertEquals("한화생명", filled[2][0])
        }
    }

    @Nested
    inner class FindColumnIndex {
        @Test
        fun `키워드로 컬럼 찾기`() {
            val headers = listOf("보험회사명", "상품명", "보험료_남자", "보험료_여자")

            assertEquals(0, HtmlExcelParser.findColumnIndex(headers, "보험회사"))
            assertEquals(2, HtmlExcelParser.findColumnIndex(headers, "남자"))
            assertNull(HtmlExcelParser.findColumnIndex(headers, "없는키워드"))
        }

        @Test
        fun `복수 컬럼 찾기`() {
            val headers = listOf("보험회사명", "보험료_남자", "보험료_여자")

            val indices = HtmlExcelParser.findColumnIndices(headers, "보험료")
            assertEquals(2, indices.size)
            assertEquals(1, indices[0])
            assertEquals(2, indices[1])
        }
    }

    @Nested
    inner class ParsedTableGetValue {
        @Test
        fun `ParsedTable에서 값 조회`() {
            val table =
                ParsedTable(
                    headers = listOf("보험회사명", "상품명", "보험료"),
                    rows = listOf(listOf("삼성생명", "종신A", "50000")),
                )

            assertEquals("삼성생명", table.getValue(table.rows[0], "보험회사"))
            assertEquals("50000", table.getValue(table.rows[0], "보험료"))
            assertNull(table.getValue(table.rows[0], "없는키워드"))
        }
    }
}
