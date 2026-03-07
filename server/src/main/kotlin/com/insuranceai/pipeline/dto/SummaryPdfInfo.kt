package com.insuranceai.pipeline.dto

/** 상품 요약서 PDF 다운로드 정보 */
data class SummaryPdfInfo(
    val fileNo: String,
    val seq: String,
    val companyName: String,
    val productName: String,
    val category: String,
    val majorCategory: String,
)

/** 상품 요약서 다운로드 결과 리포트 */
data class SummaryDownloadReport(
    val totalCollected: Int,
    val downloaded: Int,
    val skipped: Int,
    val failed: Int,
    val errors: List<String>,
    val categoryStats: Map<String, Int>,
)
