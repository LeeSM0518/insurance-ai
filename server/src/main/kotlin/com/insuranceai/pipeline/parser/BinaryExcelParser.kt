package com.insuranceai.pipeline.parser

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.nio.file.Path

/**
 * 바이너리 .xls 파일 파서 (Apache POI HSSF)
 *
 * 손보협회 비교공시 엑셀은 실제 바이너리 Excel 형식.
 * 멀티 레벨 헤더를 flatten하여 HtmlExcelParser와 동일한 ParsedTable로 변환.
 */
object BinaryExcelParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 메타/타이틀 행 스킵 기준: "선택" 또는 "회사명"이 포함된 행을 헤더 시작으로 판단 */
    private val HEADER_START_KEYWORDS = listOf("선택", "회사명", "보험회사")

    fun parse(filePath: Path): ParsedTable {
        FileInputStream(filePath.toFile()).use { fis ->
            val workbook = HSSFWorkbook(fis)
            val sheet = workbook.getSheetAt(0)

            // 병합 셀 정보 수집
            val mergedRegions = sheet.mergedRegions.toList()

            // 헤더 시작 행 찾기
            val headerStartRow = findHeaderStartRow(sheet)
            if (headerStartRow < 0) {
                throw IllegalArgumentException("헤더 행을 찾을 수 없음: $filePath")
            }

            // 헤더 행 수 결정 (연속된 헤더 행)
            val headerEndRow = findHeaderEndRow(sheet, headerStartRow)
            val dataStartRow = headerEndRow + 1

            logger.debug(
                "바이너리 Excel: 헤더=행$headerStartRow~$headerEndRow, 데이터=행$dataStartRow~${sheet.lastRowNum}",
            )

            // 컬럼 수 결정
            val maxCols = estimateColumnCount(sheet, headerStartRow, headerEndRow)

            // 멀티 레벨 헤더 flatten
            val flatHeaders = flattenHeaders(sheet, mergedRegions, headerStartRow, headerEndRow, maxCols)
            logger.debug("헤더 flatten 완료: ${flatHeaders.size}개 컬럼 — ${flatHeaders.take(5)}...")

            // 데이터 행 추출 (병합 셀 해결)
            val dataRows = extractDataRows(sheet, mergedRegions, dataStartRow, maxCols)

            workbook.close()
            return ParsedTable(flatHeaders, dataRows)
        }
    }

    /** 헤더 시작 행 찾기: "선택" 또는 "회사명"이 셀 값 전체와 정확히 일치하는 행 */
    private fun findHeaderStartRow(sheet: Sheet): Int {
        for (r in 0..minOf(15, sheet.lastRowNum)) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0 until minOf(5, row.lastCellNum.toInt())) {
                val text = getCellText(row.getCell(c))
                if (HEADER_START_KEYWORDS.any { text == it }) {
                    return r
                }
            }
        }
        return -1
    }

    /** 헤더 끝 행 찾기: 헤더 시작 후 데이터가 시작되는 지점 직전 */
    private fun findHeaderEndRow(
        sheet: Sheet,
        headerStartRow: Int,
    ): Int {
        // 헤더 시작 행의 다음 행부터 검사
        for (r in headerStartRow + 1..minOf(headerStartRow + 5, sheet.lastRowNum)) {
            val row = sheet.getRow(r) ?: continue
            // 회사명/상품명 컬럼(1,2)에 실제 데이터가 있으면 데이터 행
            val col1 = getCellText(row.getCell(1))
            if (col1.isNotBlank() && !isHeaderText(col1)) {
                return r - 1
            }
        }
        return headerStartRow
    }

    /** 헤더 텍스트인지 판별 */
    private fun isHeaderText(text: String): Boolean = text in listOf("회사명", "상품명", "채널", "담보명", "남자", "여자", "")

    /** 컬럼 수 추정 */
    private fun estimateColumnCount(
        sheet: Sheet,
        headerStart: Int,
        headerEnd: Int,
    ): Int {
        var maxCols = 0
        for (r in headerStart..headerEnd) {
            val row = sheet.getRow(r) ?: continue
            maxCols = maxOf(maxCols, row.lastCellNum.toInt())
        }
        return maxCols
    }

    /** 멀티 레벨 헤더 flatten — HtmlExcelParser와 동일한 로직 */
    private fun flattenHeaders(
        sheet: Sheet,
        mergedRegions: List<CellRangeAddress>,
        headerStart: Int,
        headerEnd: Int,
        maxCols: Int,
    ): List<String> {
        val numHeaderRows = headerEnd - headerStart + 1
        val grid = Array(numHeaderRows) { arrayOfNulls<String>(maxCols) }

        // 병합 셀 기반으로 그리드 채우기
        for (r in headerStart..headerEnd) {
            val row = sheet.getRow(r) ?: continue
            val gridRow = r - headerStart

            for (c in 0 until maxCols) {
                if (grid[gridRow][c] != null) continue // 이미 채워짐 (병합)

                val text = getCellText(row.getCell(c))
                if (text.isBlank()) continue

                // 병합 영역 확인
                val merged = findMergedRegion(mergedRegions, r, c)
                if (merged != null) {
                    for (mr in merged.firstRow..merged.lastRow) {
                        val gr = mr - headerStart
                        if (gr < 0 || gr >= numHeaderRows) continue
                        for (mc in merged.firstColumn..merged.lastColumn) {
                            if (mc < maxCols && grid[gr][mc] == null) {
                                grid[gr][mc] = text
                            }
                        }
                    }
                } else {
                    grid[gridRow][c] = text
                }
            }
        }

        // 각 컬럼에 대해 행별 텍스트를 "_"로 연결 (연속 중복 제거)
        return (0 until maxCols).map { col ->
            val parts = (0 until numHeaderRows).mapNotNull { row -> grid[row][col] }
            parts
                .filterIndexed { idx, text ->
                    idx == 0 || text != parts[idx - 1]
                }.joinToString("_")
        }
    }

    /** 데이터 행 추출 */
    private fun extractDataRows(
        sheet: Sheet,
        mergedRegions: List<CellRangeAddress>,
        dataStartRow: Int,
        maxCols: Int,
    ): List<List<String>> {
        val result = mutableListOf<List<String>>()

        for (r in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue

            // 빈 행 스킵
            val hasData = (0 until maxCols).any { c -> getCellText(row.getCell(c)).isNotBlank() }
            if (!hasData) continue

            val rowData =
                (0 until maxCols).map { c ->
                    // 병합 셀이면 병합 영역의 첫 번째 셀 값 사용
                    val merged = findMergedRegion(mergedRegions, r, c)
                    if (merged != null && (r != merged.firstRow || c != merged.firstColumn)) {
                        val firstRow = sheet.getRow(merged.firstRow)
                        getCellText(firstRow?.getCell(merged.firstColumn))
                    } else {
                        getCellText(row.getCell(c))
                    }
                }

            result.add(rowData)
        }

        return result
    }

    /** 셀 텍스트 추출 */
    private fun getCellText(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                val value = cell.numericCellValue
                // 정수면 소수점 제거
                if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue.trim()
                } catch (_: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (_: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    /** 병합 영역 찾기 */
    private fun findMergedRegion(
        mergedRegions: List<CellRangeAddress>,
        row: Int,
        col: Int,
    ): CellRangeAddress? = mergedRegions.firstOrNull { it.isInRange(row, col) }
}
