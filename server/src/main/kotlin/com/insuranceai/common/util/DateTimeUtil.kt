package com.insuranceai.common.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * KST 기반 시간 처리 유틸리티
 * 엔티티/도메인은 Instant(UTC), 표현 계층에서 KST 변환
 */
object DateTimeUtil {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    fun now(): Instant = Instant.now()

    fun toKst(instant: Instant): ZonedDateTime = instant.atZone(KST)

    fun toKstString(instant: Instant): String = toKst(instant).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun toLocalDate(instant: Instant): LocalDate = toKst(instant).toLocalDate()
}
