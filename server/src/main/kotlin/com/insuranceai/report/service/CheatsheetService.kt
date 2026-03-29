package com.insuranceai.report.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.report.domain.ReportType
import com.insuranceai.report.dto.CheatsheetRequest
import com.insuranceai.report.dto.ReportResponse
import com.insuranceai.report.dto.ReportSummaryResponse
import com.insuranceai.report.entity.ReportEntity
import com.insuranceai.report.error.ReportError
import com.insuranceai.report.repository.ReportRepository
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CheatsheetService(
    private val reportRepository: ReportRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 생성된 치트시트를 reports 테이블에 저장 */
    suspend fun saveCheatsheet(
        userId: UUID,
        content: String,
        request: CheatsheetRequest,
        chatSessionId: UUID? = null,
    ): ReportEntity {
        val metadataJson =
            Json.of(
                objectMapper.writeValueAsString(
                    mapOf(
                        "category" to request.category,
                        "companyNames" to request.companyNames,
                        "recommendedProduct" to request.recommendedProduct,
                        "customerAge" to request.customerAge,
                        "customerGender" to request.customerGender,
                    ),
                ),
            )
        val entity =
            ReportEntity(
                userId = userId,
                reportType = ReportType.CHEATSHEET.name,
                title = "${request.category} 치트시트 (${request.companyNames.joinToString(", ")})",
                content = content,
                metadata = metadataJson,
                chatSessionId = chatSessionId ?: request.chatSessionId,
            )
        return reportRepository.save(entity)
    }

    /** 내 치트시트 목록 조회 */
    suspend fun getMyCheatsheets(userId: UUID): List<ReportSummaryResponse> =
        reportRepository
            .findByUserIdAndReportTypeOrderByCreatedAtDesc(userId, ReportType.CHEATSHEET.name)
            .toList()
            .map { ReportSummaryResponse(it.id!!, it.reportType, it.title, it.createdAt) }

    /** 치트시트 상세 조회 (소유자 검증) */
    suspend fun getCheatsheet(
        reportId: UUID,
        userId: UUID,
    ): ReportResponse {
        val entity = reportRepository.findById(reportId) ?: throw ReportError.NotFound
        if (entity.userId != userId) throw ReportError.NotOwner
        return entity.toResponse()
    }

    /** 치트시트 삭제 */
    suspend fun deleteCheatsheet(
        reportId: UUID,
        userId: UUID,
    ) {
        val deleted = reportRepository.deleteByIdAndUserId(reportId, userId)
        if (deleted == 0) throw ReportError.NotFound
    }

    private fun ReportEntity.toResponse() =
        ReportResponse(
            id = id!!,
            reportType = reportType,
            title = title,
            content = content,
            metadata =
                metadata?.let {
                    @Suppress("UNCHECKED_CAST")
                    objectMapper.readValue(it.asString(), Map::class.java) as? Map<String, Any>
                },
            shareToken = shareToken,
            shareExpiresAt = shareExpiresAt,
            chatSessionId = chatSessionId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
