package com.insuranceai.search.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class SearchErrorTest {
    @Test
    fun `QueryMissing 에러 코드 자동 생성`() {
        assertEquals("SEARCH:QUERY_MISSING", SearchError.QueryMissing.code)
        assertEquals(HttpStatus.BAD_REQUEST, SearchError.QueryMissing.status)
        assertEquals("검색어를 입력해주세요", SearchError.QueryMissing.message)
    }

    @Test
    fun `QueryTooLong 에러 코드 자동 생성`() {
        assertEquals("SEARCH:QUERY_TOO_LONG", SearchError.QueryTooLong.code)
        assertEquals(HttpStatus.BAD_REQUEST, SearchError.QueryTooLong.status)
    }
}
