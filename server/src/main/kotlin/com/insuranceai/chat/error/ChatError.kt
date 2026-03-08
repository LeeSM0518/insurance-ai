package com.insuranceai.chat.error

import com.insuranceai.common.error.ServiceError
import org.springframework.http.HttpStatus

sealed class ChatError(
    override val message: String,
) : ServiceError() {
    override val mainCode = "CHAT"

    data object SessionNotFound : ChatError("세션을 찾을 수 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }

    data object NotOwner : ChatError("접근 권한이 없는 세션입니다") {
        override val status = HttpStatus.FORBIDDEN
    }

    data object RateLimitExceeded : ChatError("채팅 요청 제한을 초과했습니다") {
        override val status = HttpStatus.TOO_MANY_REQUESTS
    }

    data object LlmResponseFailed : ChatError("AI 답변 생성에 실패했습니다") {
        override val status = HttpStatus.BAD_GATEWAY
    }

    data object UserMessageNotFound : ChatError("세션에 사용자 메시지가 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }
}
