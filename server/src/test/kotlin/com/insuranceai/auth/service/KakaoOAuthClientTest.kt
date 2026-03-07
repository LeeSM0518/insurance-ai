package com.insuranceai.auth.service

import com.insuranceai.auth.error.AuthError
import com.insuranceai.common.config.AppProperties
import com.insuranceai.common.config.ChatProperties
import com.insuranceai.common.config.KakaoProperties
import com.insuranceai.common.config.RateLimitProperties
import com.insuranceai.common.config.SearchProperties
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KakaoOAuthClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val appProperties =
            AppProperties(
                kakao = KakaoProperties(clientId = "test", userInfoUrl = mockWebServer.url("/v2/user/me").toString()),
                search = SearchProperties(),
                chat = ChatProperties(),
                rateLimit = RateLimitProperties(),
            )
        kakaoOAuthClient = KakaoOAuthClient(appProperties)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `카카오 사용자 정보 조회 성공`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                            "id": 123456789,
                            "kakao_account": {
                                "profile": { "nickname": "홍길동" },
                                "email": "hong@example.com"
                            }
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json"),
            )

            val result = kakaoOAuthClient.getUserInfo("valid-token")

            assertEquals(123456789L, result.id)
            assertEquals("홍길동", result.nickname)
            assertEquals("hong@example.com", result.email)
        }

    @Test
    fun `카카오 이메일 없는 사용자 조회`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(
                        """
                        {
                            "id": 987654321,
                            "kakao_account": {
                                "profile": { "nickname": "김철수" }
                            }
                        }
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json"),
            )

            val result = kakaoOAuthClient.getUserInfo("valid-token")

            assertEquals(987654321L, result.id)
            assertEquals("김철수", result.nickname)
            assertNull(result.email)
        }

    @Test
    fun `카카오 API 에러 시 AuthError KakaoAuthFailed throw`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(401))

            assertFailsWith<AuthError.KakaoAuthFailed> {
                kakaoOAuthClient.getUserInfo("invalid-token")
            }
        }
}
