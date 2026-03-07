package com.insuranceai.auth.service

import com.fasterxml.jackson.databind.JsonNode
import com.insuranceai.auth.error.AuthError
import com.insuranceai.common.config.AppProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
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
    suspend fun exchangeCode(code: String, redirectUri: String): String {
        val response =
            runCatching {
                tokenClient
                    .post()
                    .uri("/oauth/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(
                        "grant_type=authorization_code" +
                            "&client_id=${appProperties.kakao.clientId}" +
                            "&redirect_uri=$redirectUri" +
                            "&code=$code",
                    )
                    .retrieve()
                    .awaitBody<JsonNode>()
            }.getOrElse {
                throw AuthError.KakaoAuthFailed
            }

        return response["access_token"]?.asText() ?: throw AuthError.KakaoAuthFailed
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
