package com.insuranceai.auth.filter

import com.insuranceai.auth.entity.UserEntity
import com.insuranceai.auth.repository.UserRepository
import com.insuranceai.auth.service.TokenService
import com.insuranceai.common.security.UserPrincipal
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {
    private lateinit var filter: JwtAuthenticationFilter
    private lateinit var tokenService: TokenService
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        tokenService = mockk()
        userRepository = mockk()
        filter = JwtAuthenticationFilter(tokenService, userRepository)
    }

    @Test
    fun `토큰 없는 요청은 인증 없이 통과`() {
        val request = MockServerHttpRequest.get("/api/v1/test").build()
        val exchange = MockServerWebExchange.from(request)

        var chainCalled = false
        val chain =
            WebFilterChain { _ ->
                chainCalled = true
                Mono.empty()
            }

        StepVerifier
            .create(filter.filter(exchange, chain))
            .verifyComplete()

        assert(chainCalled)
    }

    @Test
    fun `유효한 토큰 요청 시 SecurityContext에 인증 정보 설정`() {
        val userId = UUID.randomUUID()
        val user =
            UserEntity(
                id = userId,
                provider = "KAKAO",
                providerId = "12345",
                nickname = "홍길동",
            )

        coEvery { tokenService.validateAccessToken("valid-token") } returns true
        coEvery { tokenService.getUserIdFromAccessToken("valid-token") } returns userId
        coEvery { userRepository.findById(userId) } returns user

        val request =
            MockServerHttpRequest
                .get("/api/v1/test")
                .header("Authorization", "Bearer valid-token")
                .build()
        val exchange = MockServerWebExchange.from(request)

        var capturedAuthentication: UsernamePasswordAuthenticationToken? = null
        val chain =
            WebFilterChain { _ ->
                ReactiveSecurityContextHolder
                    .getContext()
                    .doOnNext { ctx ->
                        capturedAuthentication = ctx.authentication as? UsernamePasswordAuthenticationToken
                    }.then()
            }

        StepVerifier
            .create(filter.filter(exchange, chain))
            .verifyComplete()

        assertNotNull(capturedAuthentication)
        val principal = capturedAuthentication!!.principal as UserPrincipal
        assertEquals(userId, principal.userId)
        assertEquals("홍길동", principal.nickname)
    }

    @Test
    fun `만료된 토큰 요청 시 인증 없이 통과`() {
        coEvery { tokenService.validateAccessToken("expired-token") } returns false

        val request =
            MockServerHttpRequest
                .get("/api/v1/test")
                .header("Authorization", "Bearer expired-token")
                .build()
        val exchange = MockServerWebExchange.from(request)

        var capturedAuthentication: UsernamePasswordAuthenticationToken? = null
        val chain =
            WebFilterChain { _ ->
                ReactiveSecurityContextHolder
                    .getContext()
                    .doOnNext { ctx ->
                        capturedAuthentication = ctx.authentication as? UsernamePasswordAuthenticationToken
                    }.then()
            }

        StepVerifier
            .create(filter.filter(exchange, chain))
            .verifyComplete()

        assertNull(capturedAuthentication)
    }

    @Test
    fun `Bearer 접두사 없는 Authorization 헤더 무시`() {
        val request =
            MockServerHttpRequest
                .get("/api/v1/test")
                .header("Authorization", "Basic some-token")
                .build()
        val exchange = MockServerWebExchange.from(request)

        var chainCalled = false
        val chain =
            WebFilterChain { _ ->
                chainCalled = true
                Mono.empty()
            }

        StepVerifier
            .create(filter.filter(exchange, chain))
            .verifyComplete()

        assert(chainCalled)
    }
}
