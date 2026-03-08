package com.insuranceai.pipeline.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * 비교공시 .xls 파일(실제 HTML 테이블) 파서
 *
 * 생보협회 비교공시 엑셀 파일은 확장자만 .xls이고 실제 내용은 HTML 테이블.
 * Jsoup으로 HTML 파싱 후 멀티 레벨 헤더를 flatten하여 컬럼명 기반 매핑.
 */
object HtmlExcelParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * HTML 테이블 파일 파싱 → (flattenedHeaders, dataRows) 반환
     *
     * @param filePath .xls 파일 경로 (실제 HTML)
     * @return Pair(flatten된 컬럼명 리스트, 데이터 행 리스트)
     */
    fun parse(filePath: Path): ParsedTable {
        val doc: Document = Jsoup.parse(filePath.toFile(), "UTF-8")
        val table =
            doc.selectFirst("table")
                ?: throw IllegalArgumentException("HTML 테이블을 찾을 수 없음: $filePath")

        val rows = table.select("tr")
        if (rows.isEmpty()) throw IllegalArgumentException("테이블 행이 없음: $filePath")

        // 헤더 행과 데이터 행 분리 (thead/tbody 또는 상위 N행이 헤더)
        val headerRows = mutableListOf<Element>()
        val dataRows = mutableListOf<Element>()

        val thead = table.selectFirst("thead")
        val tbodies = table.select("tbody")

        if (thead != null && tbodies.isNotEmpty()) {
            headerRows.addAll(thead.select("tr"))
            // 상품당 별도 <tbody>인 경우(손보 DOM 추출 등) 전체 합산
            for (tb in tbodies) {
                dataRows.addAll(tb.select("tr"))
            }
        } else {
            // thead/tbody 구분 없으면 th 셀 포함 행을 헤더로 판별
            for (row in rows) {
                val cells = row.select("th, td")
                val thCount = row.select("th").size
                if (thCount > cells.size / 2 && dataRows.isEmpty()) {
                    headerRows.add(row)
                } else {
                    dataRows.add(row)
                }
            }
            // 헤더가 없으면 첫 행을 헤더로
            if (headerRows.isEmpty() && dataRows.isNotEmpty()) {
                headerRows.add(dataRows.removeFirst())
            }
        }

        // 멀티 레벨 헤더 flatten
        val flatHeaders = flattenHeaders(headerRows)
        logger.debug("헤더 flatten 완료: ${flatHeaders.size}개 컬럼 — ${flatHeaders.take(5)}...")

        // 데이터 행을 문자열 리스트로 변환 (rowspan/colspan 해결)
        val resolvedData = resolveSpans(dataRows, flatHeaders.size)

        // 헤더-데이터 컬럼 수 불일치 보정 (PB14 등)
        val (alignedHeaders, alignedData) = alignHeadersWithData(flatHeaders, resolvedData)

        return ParsedTable(alignedHeaders, alignedData)
    }

    /**
     * 멀티 레벨 헤더 flatten
     *
     * 3~4단계 헤더를 "_"로 연결하여 단일 레벨로 변환.
     * 예: "보장내용 및 보험료" > "보험료" > "남자" → "보장내용 및 보험료_보험료_남자"
     */
    private fun flattenHeaders(headerRows: List<Element>): List<String> {
        if (headerRows.isEmpty()) return emptyList()

        // 각 헤더 행의 셀을 파싱하여 그리드 형태로 구성
        val maxCols = estimateColumnCount(headerRows)
        val grid = Array(headerRows.size) { arrayOfNulls<String>(maxCols) }

        for ((rowIdx, row) in headerRows.withIndex()) {
            val cells = row.select("th, td")
            var colIdx = 0
            for (cell in cells) {
                // 이미 채워진 셀 건너뛰기 (이전 행의 rowspan)
                while (colIdx < maxCols && grid[rowIdx][colIdx] != null) {
                    colIdx++
                }
                if (colIdx >= maxCols) break

                val text = cell.text().trim()
                val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1

                // rowspan/colspan 만큼 그리드 채우기
                for (r in rowIdx until (rowIdx + rowspan).coerceAtMost(headerRows.size)) {
                    for (c in colIdx until (colIdx + colspan).coerceAtMost(maxCols)) {
                        grid[r][c] = text
                    }
                }
                colIdx += colspan
            }
        }

        // 각 컬럼에 대해 행별 텍스트를 "_"로 연결 (중복 제거)
        return (0 until maxCols).map { col ->
            val parts = (0 until headerRows.size).mapNotNull { row -> grid[row][col] }
            // 연속 중복 제거 (부모와 같은 텍스트 제거)
            parts
                .filterIndexed { idx, text ->
                    idx == 0 || text != parts[idx - 1]
                }.joinToString("_")
        }
    }

    /** 헤더 행들에서 최대 컬럼 수 추정 */
    private fun estimateColumnCount(headerRows: List<Element>): Int {
        var maxCols = 0
        for (row in headerRows) {
            var cols = 0
            for (cell in row.select("th, td")) {
                cols += cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            }
            maxCols = maxOf(maxCols, cols)
        }
        return maxCols
    }

    /**
     * 데이터 행의 rowspan/colspan 해결
     *
     * forward-fill 포함: 보험회사명/상품명이 그룹 내 첫 행에만 있고 나머지는 빈칸.
     * PB14 display:none td 병합: style="display:none" td는 이전 td에 텍스트 병합 후 제거.
     */
    private fun resolveSpans(
        dataRows: List<Element>,
        expectedCols: Int,
    ): List<List<String>> {
        // rowspan 추적용 버퍼: [colIdx] = (남은 행 수, 텍스트)
        val rowspanBuffer = Array<Pair<Int, String>?>(expectedCols) { null }
        val result = mutableListOf<List<String>>()

        for (row in dataRows) {
            // display:none td 병합 전처리 (PB14 등)
            val cells = mergeHiddenCells(row.select("th, td"))
            val rowData = Array(expectedCols) { "" }
            var colIdx = 0
            var cellIdx = 0

            while (colIdx < expectedCols) {
                // rowspan 버퍼에서 값 채우기
                val buffer = rowspanBuffer[colIdx]
                if (buffer != null && buffer.first > 0) {
                    rowData[colIdx] = buffer.second
                    rowspanBuffer[colIdx] = buffer.first - 1 to buffer.second
                    if (buffer.first - 1 <= 0) rowspanBuffer[colIdx] = null
                    colIdx++
                    continue
                }

                if (cellIdx >= cells.size) {
                    colIdx++
                    continue
                }

                val cell = cells[cellIdx]
                val colspan = cell.first
                val rowspan = cell.second
                val text = cell.third

                for (c in colIdx until (colIdx + colspan).coerceAtMost(expectedCols)) {
                    rowData[c] = text
                    if (rowspan > 1) {
                        rowspanBuffer[c] = (rowspan - 1) to text
                    }
                }
                colIdx += colspan
                cellIdx++
            }

            result.add(rowData.toList())
        }

        return result
    }

    /**
     * display:none td 병합: style에 "display:none" 또는 "display: none"이 있는 td의
     * 텍스트를 이전 td에 병합하고 해당 td를 제거.
     * PB14에서 특이사항이 2개 td로 나뉘는 문제 해결.
     *
     * @return (colspan, rowspan, text) 트리플 리스트
     */
    private fun mergeHiddenCells(cells: List<Element>): List<Triple<Int, Int, String>> {
        val result = mutableListOf<Triple<Int, Int, String>>()

        for (cell in cells) {
            val style = cell.attr("style").lowercase()
            val isHidden = style.contains("display") && style.contains("none")
            val text = cell.text().trim()
            val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1

            if (isHidden && result.isNotEmpty()) {
                // hidden td의 텍스트를 이전 td에 병합
                val prev = result.removeLast()
                val mergedText = if (text.isNotBlank()) text else prev.third
                result.add(Triple(prev.first, prev.second, mergedText))
            } else {
                result.add(Triple(colspan, rowspan, text))
            }
        }

        return result
    }

    /**
     * Forward-fill: 보험회사명/상품명이 그룹 첫 행에만 있는 경우 이전 값으로 채움
     *
     * @param rows 데이터 행 리스트
     * @param fillColumns fill 대상 컬럼 인덱스
     */
    fun forwardFill(
        rows: List<List<String>>,
        fillColumns: List<Int>,
    ): List<List<String>> {
        if (rows.isEmpty()) return rows

        val result = mutableListOf<List<String>>()
        val lastValues = mutableMapOf<Int, String>()

        for (row in rows) {
            val mutableRow = row.toMutableList()
            for (colIdx in fillColumns) {
                if (colIdx >= mutableRow.size) continue
                val currentValue = mutableRow[colIdx].trim()
                if (currentValue.isNotBlank() && !ValueCleaner.isNull(currentValue)) {
                    lastValues[colIdx] = currentValue
                } else {
                    lastValues[colIdx]?.let { mutableRow[colIdx] = it }
                }
            }
            result.add(mutableRow)
        }

        return result
    }

    /**
     * 헤더-데이터 컬럼 수 불일치 보정
     *
     * PB14(상해) 등에서 헤더 수 < 데이터 컬럼 수인 경우,
     * 데이터 행의 초과 컬럼(빈 값인 컬럼)을 제거하여 정렬합니다.
     */
    private fun alignHeadersWithData(
        headers: List<String>,
        dataRows: List<List<String>>,
    ): Pair<List<String>, List<List<String>>> {
        if (dataRows.isEmpty()) return headers to dataRows

        // 데이터 행의 실제 컬럼 수 확인 (대부분의 행에서 공통적인 수)
        val dataColCount = dataRows.first().size
        val headerColCount = headers.size

        if (dataColCount <= headerColCount) return headers to dataRows

        val diff = dataColCount - headerColCount
        logger.warn("헤더-데이터 컬럼 수 불일치 감지: 헤더=$headerColCount, 데이터=$dataColCount (차이=$diff) — 초과 컬럼 제거 시도")

        // 초과 컬럼 위치 찾기: 대다수 행에서 빈 값인 컬럼을 후보로 선정
        val emptyCounts = IntArray(dataColCount)
        for (row in dataRows) {
            for (i in row.indices) {
                if (row[i].isBlank()) emptyCounts[i]++
            }
        }

        // 빈 비율이 가장 높은 컬럼부터 diff개 선택 (헤더에 매핑되지 않는 초과 컬럼)
        val threshold = dataRows.size * 0.5
        val candidateIndices =
            emptyCounts
                .withIndex()
                .filter { it.value >= threshold }
                .sortedByDescending { it.value }
                .take(diff)
                .map { it.index }
                .toSet()

        if (candidateIndices.size < diff) {
            // 빈 컬럼으로 충분히 제거 불가 시, 헤더를 확장
            logger.warn("빈 컬럼이 부족하여 헤더 확장으로 보정: ${diff - candidateIndices.size}개 빈 헤더 추가")
            val extendedHeaders = headers.toMutableList()
            repeat(diff - candidateIndices.size) { extendedHeaders.add("") }
            val filteredData =
                if (candidateIndices.isNotEmpty()) {
                    dataRows.map { row -> row.filterIndexed { idx, _ -> idx !in candidateIndices } }
                } else {
                    dataRows
                }
            return extendedHeaders to filteredData
        }

        // 초과 컬럼 제거
        val alignedData = dataRows.map { row -> row.filterIndexed { idx, _ -> idx !in candidateIndices } }
        return headers to alignedData
    }

    /**
     * 컬럼명으로 인덱스 찾기 (부분 매칭 지원)
     *
     * @param headers flatten된 헤더 리스트
     * @param keyword 검색할 키워드 (부분 매칭)
     * @return 첫 번째 매칭 컬럼 인덱스 (없으면 null)
     */
    fun findColumnIndex(
        headers: List<String>,
        keyword: String,
    ): Int? = headers.indexOfFirst { it.contains(keyword) }.takeIf { it >= 0 }

    /**
     * 컬럼명으로 복수 인덱스 찾기
     */
    fun findColumnIndices(
        headers: List<String>,
        keyword: String,
    ): List<Int> = headers.indices.filter { headers[it].contains(keyword) }
}

/**
 * 파싱된 테이블 데이터
 */
data class ParsedTable(
    val headers: List<String>,
    val rows: List<List<String>>,
) {
    /** 특정 컬럼 값 조회 (행, 컬럼명 키워드) */
    fun getValue(
        row: List<String>,
        columnKeyword: String,
    ): String? {
        val idx = HtmlExcelParser.findColumnIndex(headers, columnKeyword) ?: return null
        return if (idx < row.size) ValueCleaner.cleanString(row[idx]) else null
    }

    /** 특정 컬럼 값 조회 (인덱스) */
    fun getValueByIndex(
        row: List<String>,
        colIdx: Int,
    ): String? = if (colIdx in row.indices) ValueCleaner.cleanString(row[colIdx]) else null
}
