package com.insuranceai.common.error

import com.insuranceai.common.util.TextUtil.toScreamingSnakeCase
import org.springframework.http.HttpStatus

/**
 * 도메인별 sealed class 에러의 공통 베이스
 * 에러 코드를 "도메인:에러명" 형식으로 자동 생성
 *
 * 사용 예:
 * ```
 * sealed class AuthError(override val message: String) : ServiceError() {
 *     override val mainCode = "AUTH"
 *
 *     data object TokenExpired : AuthError("토큰이 만료되었습니다") {
 *         override val status = HttpStatus.UNAUTHORIZED
 *     }
 * }
 * ```
 * → AuthError.TokenExpired.code == "AUTH:TOKEN_EXPIRED"
 */
abstract class ServiceError : RuntimeException() {
    /** 도메인 코드: "AUTH", "CHAT", "SEARCH", "POLICY" */
    abstract val mainCode: String

    abstract val status: HttpStatus

    abstract override val message: String

    /**
     * 자동 생성 에러 코드
     * 클래스명 CamelCase → SCREAMING_SNAKE_CASE 변환
     * 예: TokenExpired → "AUTH:TOKEN_EXPIRED"
     */
    val code: String
        get() = "$mainCode:${this::class.simpleName?.toScreamingSnakeCase() ?: "UNKNOWN"}"
}
