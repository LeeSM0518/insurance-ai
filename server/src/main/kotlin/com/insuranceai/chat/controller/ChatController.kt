package com.insuranceai.chat.controller

import com.insuranceai.chat.dto.ChatMessageRequest
import com.insuranceai.chat.dto.ChatSessionResponse
import com.insuranceai.chat.dto.ChatStreamEvent
import com.insuranceai.chat.dto.CreateSessionRequest
import com.insuranceai.chat.dto.SessionMessagesResponse
import com.insuranceai.chat.service.ChatService
import com.insuranceai.chat.usecase.SendChatMessageUseCase
import com.insuranceai.common.dto.ServiceResponse
import com.insuranceai.common.security.UserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "AI 채팅", description = "AI 질의응답 API")
@SecurityRequirement(name = "bearerAuth")
class ChatController(
    private val chatService: ChatService,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
) {
    @PostMapping("/sessions")
    @Operation(summary = "채팅 세션 생성")
    suspend fun createSession(
        @Valid @RequestBody request: CreateSessionRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ServiceResponse<ChatSessionResponse>> {
        val response = chatService.createSession(principal.userId, request.initialMessage)
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.created(response))
    }

    @GetMapping("/sessions")
    @Operation(summary = "채팅 세션 목록 조회")
    suspend fun getSessions(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ServiceResponse<List<ChatSessionResponse>>> {
        val response = chatService.getSessions(principal.userId)
        return ResponseEntity.ok(ServiceResponse.ok(response))
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "채팅 메시지 이력 조회")
    suspend fun getMessages(
        @PathVariable sessionId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ServiceResponse<SessionMessagesResponse>> {
        val response = chatService.getMessages(sessionId, principal.userId)
        return ResponseEntity.ok(ServiceResponse.ok(response))
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "채팅 세션 삭제")
    suspend fun deleteSession(
        @PathVariable sessionId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Unit> {
        chatService.deleteSession(sessionId, principal.userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/sessions/{sessionId}/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "채팅 메시지 전송 (SSE 스트리밍)")
    fun sendMessage(
        @PathVariable sessionId: UUID,
        @Valid @RequestBody request: ChatMessageRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): Flux<ServerSentEvent<ChatStreamEvent>> =
        sendChatMessageUseCase
            .execute(sessionId, request.content, principal.userId)
            .map { event -> ServerSentEvent.builder(event).build() }
}
