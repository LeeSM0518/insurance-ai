package com.insuranceai.policy.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class PolicyErrorTest {
    @Test
    fun `ArticleNotFound 에러 코드 자동 생성`() {
        assertEquals("POLICY:ARTICLE_NOT_FOUND", PolicyError.ArticleNotFound.code)
        assertEquals(HttpStatus.NOT_FOUND, PolicyError.ArticleNotFound.status)
        assertEquals("약관 조문을 찾을 수 없습니다", PolicyError.ArticleNotFound.message)
    }
}
