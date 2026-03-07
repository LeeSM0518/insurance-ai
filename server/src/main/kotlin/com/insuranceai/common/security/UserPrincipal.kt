package com.insuranceai.common.security

import java.util.UUID

/**
 * 경량 인증 주체 data class
 * @AuthenticationPrincipal로 컨트롤러에서 주입
 */
data class UserPrincipal(
    val userId: UUID,
    val nickname: String,
)
