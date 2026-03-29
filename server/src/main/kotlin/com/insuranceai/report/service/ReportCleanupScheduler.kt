package com.insuranceai.report.service

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 매일 자정에 만료된 공유 토큰을 정리하는 스케줄러.
 * 리포트 자체는 삭제하지 않고 share_token, share_expires_at만 null로 설정합니다.
 */
@Component
class ReportCleanupScheduler(
    private val dsl: DSLContext,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *")
    fun cleanupExpiredShareLinks() {
        val reports = DSL.table("reports")
        val updated =
            Mono
                .from(
                    dsl
                        .update(reports)
                        .setNull(DSL.field("share_token", String::class.java))
                        .setNull(DSL.field("share_expires_at", Instant::class.java))
                        .where(
                            DSL
                                .field("share_expires_at", Instant::class.java)
                                .lt(Instant.now()),
                        ),
                ).block() ?: 0
        if (updated > 0) {
            logger.info("만료된 공유 링크 정리: ${updated}건")
        }
    }
}
