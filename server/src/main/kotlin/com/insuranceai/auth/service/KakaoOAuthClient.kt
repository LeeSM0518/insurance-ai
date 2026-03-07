package com.insuranceai.auth.service

import com.fasterxml.jackson.databind.JsonNode
import com.insuranceai.auth.error.AuthError
import com.insuranceai.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

data class KakaoUserInfo(
    val id: Long,
    val nickname: String,
    val email: String? = null,
)

@Component
class KakaoOAuthClient(
    private val appProperties: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient =
        WebClient
            .builder()
            .baseUrl(appProperties.kakao.userInfoUrl)
            .build()

    private val tokenClient =
        WebClient
            .builder()
            .baseUrl("https://kauth.kakao.com")
            .build()

    /**
     * authorization_code를 카카오 access_token으로 교환합니다.
     */
    suspend fun exchangeCode(
        code: String,
        redirectUri: String,
    ): String {
        log.debug("카카오 토큰 교환 요청: clientId={}, redirectUri={}", appProperties.kakao.clientId, redirectUri)

        val response =
            runCatching {
                tokenClient
                    .post()
                    .uri("/oauth/token")
                    .body(
                        BodyInserters
                            .fromFormData("grant_type", "authorization_code")
                            .with("client_id", appProperties.kakao.clientId)
                            .with("client_secret", appProperties.kakao.clientSecret)
                            .with("redirect_uri", redirectUri)
                            .with("code", code),
                    ).retrieve()
                    .awaitBody<JsonNode>()
            }.getOrElse { ex ->
                val body =
                    if (ex is WebClientResponseException) {
                        ex.responseBodyAsString
                    } else {
                        "N/A"
                    }
                log.error("카카오 토큰 교환 실패: {} | 응답: {}", ex.message, body)
                throw AuthError.KakaoAuthFailed
            }

        val accessToken = response["access_token"]?.asText()
        if (accessToken == null) {
            log.error("카카오 토큰 응답에 access_token 없음: {}", response)
            throw AuthError.KakaoAuthFailed
        }
        return accessToken
    }

    suspend fun getUserInfo(kakaoAccessToken: String): KakaoUserInfo {
        val response =
            runCatching {
                webClient
                    .get()
                    .header("Authorization", "Bearer $kakaoAccessToken")
                    .retrieve()
                    .awaitBody<JsonNode>()
            }.getOrElse {
                throw AuthError.KakaoAuthFailed
            }

        val id = response["id"]?.asLong() ?: throw AuthError.KakaoAuthFailed
        val kakaoAccount = response["kakao_account"]
        val nickname = kakaoAccount?.get("profile")?.get("nickname")?.asText() ?: "사용자"
        val email = kakaoAccount?.get("email")?.asText()

        return KakaoUserInfo(
            id = id,
            nickname = nickname,
            email = email,
        )
    }
}
