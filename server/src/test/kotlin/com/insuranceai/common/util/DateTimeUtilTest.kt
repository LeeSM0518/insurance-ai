package com.insuranceai.common.util

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DateTimeUtilTest {
    @Test
    fun `KST 존 ID가 Asia Seoul`() {
        assertEquals(ZoneId.of("Asia/Seoul"), DateTimeUtil.KST)
    }

    @Test
    fun `now는 현재 시각을 반환`() {
        val before = Instant.now()
        val result = DateTimeUtil.now()
        val after = Instant.now()
        assertTrue(result >= before && result <= after)
    }

    @Test
    fun `toKst는 KST 존으로 변환`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val kst = DateTimeUtil.toKst(instant)
        assertEquals(ZoneId.of("Asia/Seoul"), kst.zone)
        assertEquals(9, kst.hour) // UTC+9
    }

    @Test
    fun `toKstString은 ISO 8601 형식 반환`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val result = DateTimeUtil.toKstString(instant)
        assertNotNull(result)
        assertTrue(result.contains("+09:00"))
        assertTrue(result.startsWith("2026-01-01T09:00:00"))
    }

    @Test
    fun `toLocalDate는 KST 기준 날짜 반환`() {
        // UTC 23시 → KST 다음날 08시
        val instant = Instant.parse("2026-01-01T23:00:00Z")
        val localDate = DateTimeUtil.toLocalDate(instant)
        assertEquals(2026, localDate.year)
        assertEquals(1, localDate.monthValue)
        assertEquals(2, localDate.dayOfMonth) // KST에서는 1월 2일
    }
}
