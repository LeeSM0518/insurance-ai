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
    appProperties: AppProperties,
) {
    private val webClient =
        WebClient
            .builder()
            .baseUrl(appProperties.kakao.userInfoUrl)
            .build()

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
