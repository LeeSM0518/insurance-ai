package com.insuranceai.common.fixture

import com.insuranceai.auth.entity.RefreshTokenEntity
import com.insuranceai.auth.entity.UserEntity
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 사용자 관련 테스트 데이터 빌더.
 */
object UserFixtures {
    fun entity(
        provider: String = "kakao",
        providerId: String = "test-${UUID.randomUUID()}",
        nickname: String = "테스트 사용자",
        email: String? = "test@example.com",
    ) = UserEntity(
        provider = provider,
        providerId = providerId,
        nickname = nickname,
        email = email,
    )

    fun refreshTokenEntity(
        userId: UUID,
        token: String = UUID.randomUUID().toString(),
        familyId: UUID = UUID.randomUUID(),
        expiresAt: Instant = Instant.now().plus(30, ChronoUnit.DAYS),
    ) = RefreshTokenEntity(
        userId = userId,
        token = token,
        familyId = familyId,
        revoked = false,
        replacedBy = null,
        expiresAt = expiresAt,
    )

    fun expiredRefreshTokenEntity(userId: UUID) =
        refreshTokenEntity(
            userId = userId,
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
        )
}
