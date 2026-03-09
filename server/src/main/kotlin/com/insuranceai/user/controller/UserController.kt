package com.insuranceai.user.controller

import com.insuranceai.auth.dto.UserResponse
import com.insuranceai.auth.error.AuthError
import com.insuranceai.auth.repository.UserRepository
import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "사용자", description = "사용자 정보 API")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userRepository: UserRepository,
) {
    @GetMapping("/me")
    suspend fun getMe(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ServiceResponse<UserResponse> {
        val user = userRepository.findById(principal.userId) ?: throw AuthError.TokenInvalid
        return ServiceResponse.ok(UserResponse.from(user))
    }
}
