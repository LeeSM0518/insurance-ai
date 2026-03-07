package com.insuranceai.auth.dto

data class LoginRequest(
    val kakaoAccessToken: String? = null,
    val code: String? = null,
    val redirectUri: String? = null,
)
