package com.insuranceai.pipeline.parser

/**
 * 값 파싱 결과를 명시적으로 표현.
 * - Success: 파싱 성공 (값 포함)
 * - Empty: 원본 데이터가 없음 (null 표현식, "-", "해당없음" 등)
 * - Failure: 원본 데이터가 존재하나 파싱 실패
 */
sealed class ParseResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ParseResult<T>()

    data object Empty : ParseResult<Nothing>()

    data class Failure(
        val rawValue: String,
        val reason: String,
    ) : ParseResult<Nothing>()
}

/** 성공 값 또는 null 반환 (기존 호환) */
fun <T> ParseResult<T>.getOrNull(): T? =
    when (this) {
        is ParseResult.Success -> value
        else -> null
    }

/** Failure이면 true */
fun <T> ParseResult<T>.isFailure(): Boolean = this is ParseResult.Failure
