package com.insuranceai.common.dto

import com.insuranceai.common.error.ServiceError
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceResponseTest {
    @Test
    fun `ok는 성공 응답 생성`() {
        val response = ServiceResponse.ok("데이터")
        assertTrue(response.success)
        assertEquals(200, response.statusCode)
        assertEquals("데이터", response.data)
        assertNull(response.code)
        assertNull(response.message)
    }

    @Test
    fun `ok는 커스텀 상태 코드 지원`() {
        val response = ServiceResponse.ok("데이터", statusCode = 202)
        assertEquals(202, response.statusCode)
    }

    @Test
    fun `created는 201 응답 생성`() {
        val response = ServiceResponse.created(mapOf("id" to "123"))
        assertTrue(response.success)
        assertEquals(201, response.statusCode)
        assertEquals(mapOf("id" to "123"), response.data)
    }

    @Test
    fun `error는 에러 응답 생성`() {
        val response = ServiceResponse.error("AUTH:TOKEN_EXPIRED", "토큰 만료", 401)
        assertFalse(response.success)
        assertEquals(401, response.statusCode)
        assertEquals("AUTH:TOKEN_EXPIRED", response.code)
        assertEquals("토큰 만료", response.message)
        assertNull(response.data)
    }

    @Test
    fun `fromServiceError는 ServiceError로부터 응답 생성`() {
        val error =
            object : ServiceError() {
                override val mainCode = "TEST"
                override val status = HttpStatus.NOT_FOUND
                override val message = "테스트 에러"
            }
        val response = ServiceResponse.fromServiceError(error)
        assertFalse(response.success)
        assertEquals(404, response.statusCode)
        assertEquals("테스트 에러", response.message)
    }
}
