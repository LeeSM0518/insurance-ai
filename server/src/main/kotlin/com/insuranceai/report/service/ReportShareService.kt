package com.insuranceai.report.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.chat.service.ChatService
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.report.domain.ReportType
import com.insuranceai.report.dto.FollowupReportRequest
import com.insuranceai.report.dto.ReportResponse
import com.insuranceai.report.dto.ReportSummaryResponse
import com.insuranceai.report.dto.ShareLinkResponse
import com.insuranceai.report.dto.SharedReportResponse
import com.insuranceai.report.entity.ReportEntity
import com.insuranceai.report.error.ReportError
import com.insuranceai.report.repository.ReportRepository
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReportShareService(
    private val reportRepository: ReportRepository,
    private val chatService: ChatService,
    private val promptLoader: PromptLoader,
    private val suspendingChatClient: SuspendingChatClient,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 팔로업 리포트에 포함할 최대 대화 메시지 수 */
        private const val MAX_CONVERSATION_MESSAGES = 10

        /** 대화 텍스트 최대 길이 (약 4000토큰 상당) */
        private const val MAX_CONVERSATION_CHARS = 12000
    }

    /**
     * 채팅 세션 기반 팔로업 리포트 생성
     * 1. 채팅 세션의 전체 대화 내용 조회
     * 2. 대화 내용을 LLM에 전달하여 요약 리포트 생성
     * 3. reports 테이블에 저장 + 공유 토큰 생성
     */
    suspend fun generateFollowupReport(
        request: FollowupReportRequest,
        userId: UUID,
    ): ReportResponse {
        // 1. 채팅 세션 소유자 검증 + 메시지 조회
        val session = chatService.getMessages(request.chatSessionId, userId)
        if (session.messages.isEmpty()) throw ReportError.NotFound

        // 2. 대화 내용 텍스트 구성 (최근 10개 메시지, 최대 12000자로 제한)
        val recentMessages = session.messages.takeLast(MAX_CONVERSATION_MESSAGES)
        val conversationText =
            recentMessages
                .joinToString("\n\n") { msg ->
                    val role = if (msg.role == "USER") "설계사" else "AI"
                    "[$role]: ${msg.content}"
                }.take(MAX_CONVERSATION_CHARS)

        // 3. LLM으로 요약 리포트 생성 (비스트리밍)
        val systemPrompt = promptLoader.load("report/followup-summary.st")
        val userPrompt = buildFollowupUserPrompt(conversationText, request)
        val reportContent = suspendingChatClient.call(userPrompt, systemPrompt)

        // 4. 설계사 연락처 섹션 추가
        val finalContent = appendAgentInfo(reportContent, request)

        // 5. 공유 토큰 생성 + 저장
        val shareToken =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(32)
        val expiresAt = Instant.now().plus(7, ChronoUnit.DAYS)

        val metadataJson =
            Json.of(
                objectMapper.writeValueAsString(
                    mapOf(
                        "chatSessionId" to request.chatSessionId.toString(),
                        "agentName" to request.agentName,
                        "includeComparison" to request.includeComparison,
                    ),
                ),
            )

        val entity =
            ReportEntity(
                userId = userId,
                reportType = ReportType.FOLLOWUP.name,
                title = request.title ?: "상담 리포트",
                content = finalContent,
                metadata = metadataJson,
                shareToken = shareToken,
                shareExpiresAt = expiresAt,
                chatSessionId = request.chatSessionId,
            )
        val saved = reportRepository.save(entity)
        logger.info("팔로업 리포트 생성: reportId=${saved.id}, shareToken=$shareToken")

        return saved.toResponse()
    }

    /**
     * 기존 리포트(치트시트/비교)에 공유 링크 생성
     */
    suspend fun createShareLink(
        reportId: UUID,
        userId: UUID,
        expiresInDays: Int = 7,
    ): ShareLinkResponse {
        val entity = reportRepository.findById(reportId) ?: throw ReportError.NotFound
        if (entity.userId != userId) throw ReportError.NotOwner

        val shareToken =
            entity.shareToken ?: UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(32)
        val expiresAt = Instant.now().plus(expiresInDays.toLong(), ChronoUnit.DAYS)

        val updated =
            entity.copy(
                shareToken = shareToken,
                shareExpiresAt = expiresAt,
                updatedAt = Instant.now(),
            )
        reportRepository.save(updated)

        return ShareLinkResponse(
            shareToken = shareToken,
            shareUrl = "/api/v1/shared/$shareToken",
            expiresAt = expiresAt,
        )
    }

    /**
     * 공유 토큰으로 리포트 조회 (인증 불필요)
     */
    suspend fun getSharedReport(shareToken: String): SharedReportResponse {
        val entity = reportRepository.findByShareToken(shareToken) ?: throw ReportError.NotFound

        // 만료 검증
        entity.shareExpiresAt?.let { expiresAt ->
            if (Instant.now().isAfter(expiresAt)) {
                throw ReportError.ShareLinkExpired
            }
        }

        return SharedReportResponse(
            reportType = entity.reportType,
            title = entity.title,
            content = entity.content,
            createdAt = entity.createdAt,
            expiresAt = entity.shareExpiresAt,
        )
    }

    /**
     * 내 리포트 전체 목록 조회
     */
    suspend fun getMyReports(userId: UUID): List<ReportSummaryResponse> =
        reportRepository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .toList()
            .map { ReportSummaryResponse(it.id!!, it.reportType, it.title, it.createdAt) }

    /**
     * 리포트 상세 조회 (소유자 검증, 범용)
     */
    suspend fun getReport(
        reportId: UUID,
        userId: UUID,
    ): ReportResponse {
        val entity = reportRepository.findById(reportId) ?: throw ReportError.NotFound
        if (entity.userId != userId) throw ReportError.NotOwner
        return entity.toResponse()
    }

    /**
     * 리포트 삭제
     */
    suspend fun deleteReport(
        reportId: UUID,
        userId: UUID,
    ) {
        val deleted = reportRepository.deleteByIdAndUserId(reportId, userId)
        if (deleted == 0) throw ReportError.NotFound
    }

    private fun buildFollowupUserPrompt(
        conversationText: String,
        request: FollowupReportRequest,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("## 상담 대화 내용")
        sb.appendLine(conversationText)
        sb.appendLine()
        sb.appendLine("## 리포트 설정")
        sb.appendLine("- 비교표 포함: ${if (request.includeComparison) "예" else "아니오"}")
        sb.appendLine("- 출처 포함: ${if (request.includeSources) "예" else "아니오"}")
        request.title?.let { sb.appendLine("- 리포트 제목: $it") }
        return sb.toString()
    }

    private fun appendAgentInfo(
        content: String,
        request: FollowupReportRequest,
    ): String {
        val sb = StringBuilder(content)
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("⚠️ 본 리포트는 AI 분석 참고용이며, 정확한 내용은 약관 원문을 확인하세요.")
        sb.appendLine()
        if (request.agentName != null || request.agentContact != null) {
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("**담당 설계사 정보**")
            request.agentName?.let { sb.appendLine("- 이름: $it") }
            request.agentContact?.let { sb.appendLine("- 연락처: $it") }
        }
        return sb.toString()
    }

    private fun ReportEntity.toResponse() =
        ReportResponse(
            id = id!!,
            reportType = reportType,
            title = title,
            content = content,
            metadata =
                metadata?.let {
                    objectMapper.readValue(it.asString(), object : TypeReference<Map<String, Any>>() {})
                },
            shareToken = shareToken,
            shareExpiresAt = shareExpiresAt,
            chatSessionId = chatSessionId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
