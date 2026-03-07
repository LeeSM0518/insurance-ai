package com.insuranceai.pipeline.parser

import org.slf4j.LoggerFactory

/**
 * 파싱 과정에서 발생하는 경고를 수집.
 * 배치 단위로 생성하여 파싱 완료 후 결과를 집계.
 *
 * 로깅 전략:
 * - DEBUG: 개별 실패 발생 즉시 (디버깅용, 원본 값/사유 포함)
 * - WARN: 파일 단위 파싱 완료 후 요약 (운영 모니터링용)
 * - INFO: 배치 전체 집계 (대시보드/알림용)
 */
class ParsingWarningCollector {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val _warnings = mutableListOf<ParsingWarning>()
    val warnings: List<ParsingWarning> get() = _warnings

    fun add(
        field: String,
        rawValue: String,
        reason: String,
        context: String,
    ) {
        _warnings.add(ParsingWarning(field, rawValue, reason, context))
        logger.debug("파싱 실패: [{}] {} = '{}' ({})", context, field, rawValue, reason)
    }

    /** ParseResult.Failure를 자동 수집하고 null 반환 */
    fun <T> collectOrNull(
        result: ParseResult<T>,
        field: String,
        context: String,
    ): T? =
        when (result) {
            is ParseResult.Success -> result.value
            is ParseResult.Empty -> null
            is ParseResult.Failure -> {
                add(field, result.rawValue, result.reason, context)
                null
            }
        }

    val failureCount: Int get() = _warnings.size

    /** 필드별 그룹핑 요약 (WARN 로깅용) */
    fun summary(): String =
        if (_warnings.isEmpty()) {
            "파싱 경고 없음"
        } else {
            buildString {
                appendLine("파싱 경고 ${_warnings.size}건:")
                _warnings.groupBy { it.field }.forEach { (field, items) ->
                    appendLine("  $field: ${items.size}건")
                    items.take(3).forEach { appendLine("    - [${it.context}] '${it.rawValue}' (${it.reason})") }
                    if (items.size > 3) appendLine("    - ... 외 ${items.size - 3}건")
                }
            }
        }
}

data class ParsingWarning(
    val field: String,
    val rawValue: String,
    val reason: String,
    val context: String,
)
