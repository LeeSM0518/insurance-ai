package com.insuranceai.evaluation.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.insuranceai.evaluation.domain.DatasetStatus
import com.insuranceai.evaluation.entity.GoldenDatasetEntity
import com.insuranceai.evaluation.repository.GoldenDatasetRepository
import com.insuranceai.evaluation.repository.GoldenQaPairRepository
import com.insuranceai.evaluation.repository.GoldenQaSourceRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.util.UUID

/** QA 쌍 + 출처 JSON export/import용 DTO */
data class QaPairExportDto(
    val id: UUID,
    val qaCode: String,
    val category: String,
    val questionType: String,
    val difficulty: String,
    val riskLevel: String,
    val question: String,
    val expectedAnswer: String,
    val mustInclude: List<String>,
    val mustNotInclude: List<String>,
    val sourceMustMatch: Boolean,
    val status: String,
    val notes: String?,
    val sources: List<QaSourceExportDto>,
)

data class QaSourceExportDto(
    val policyArticleId: UUID,
    val insuranceCompany: String,
    val productName: String,
    val articleNumber: String?,
    val contentType: String?,
)

data class DatasetExportDto(
    val datasetId: UUID,
    val name: String,
    val version: String,
    val qaPairs: List<QaPairExportDto>,
)

@Service
class GoldenDatasetService(
    private val datasetRepository: GoldenDatasetRepository,
    private val qaPairRepository: GoldenQaPairRepository,
    private val qaSourceRepository: GoldenQaSourceRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 데이터셋 생성 */
    suspend fun createDataset(
        name: String,
        version: String = "1.0.0",
        description: String? = null,
    ): GoldenDatasetEntity {
        val existing = datasetRepository.findByNameAndVersion(name, version)
        if (existing != null) {
            log.info("기존 데이터셋 사용: {} v{}", name, version)
            return existing
        }

        val entity =
            datasetRepository.save(
                GoldenDatasetEntity(
                    name = name,
                    version = version,
                    description = description,
                ),
            )
        log.info("데이터셋 생성: {} v{} (id={})", name, version, entity.id)
        return entity
    }

    /** 데이터셋 조회 */
    suspend fun getDataset(datasetId: UUID): GoldenDatasetEntity =
        datasetRepository.findById(datasetId)
            ?: error("데이터셋을 찾을 수 없습니다: $datasetId")

    /** QA 쌍 수 조회 */
    suspend fun getQaPairCount(datasetId: UUID): Long = qaPairRepository.countByDatasetId(datasetId)

    /** 데이터셋 total_count 갱신 */
    suspend fun updateTotalCount(datasetId: UUID) {
        val count = qaPairRepository.countByDatasetId(datasetId)
        val dataset = getDataset(datasetId)
        datasetRepository.save(
            dataset.copy(
                totalCount = count.toInt(),
                updatedAt = Instant.now(),
            ),
        )
    }

    /** 데이터셋 상태 변경 */
    suspend fun updateStatus(
        datasetId: UUID,
        status: DatasetStatus,
    ) {
        val dataset = getDataset(datasetId)
        datasetRepository.save(
            dataset.copy(
                status = status.name,
                updatedAt = Instant.now(),
            ),
        )
        log.info("데이터셋 상태 변경: {} → {}", datasetId, status)
    }

    /** JSON export (수동 리뷰용) */
    suspend fun exportToJson(
        datasetId: UUID,
        outputPath: String,
    ) {
        val dataset = getDataset(datasetId)
        val qaPairs = qaPairRepository.findByDatasetId(datasetId).toList()

        val exportDtos =
            qaPairs.map { pair ->
                val sources = qaSourceRepository.findByQaPairId(pair.id!!).toList()
                QaPairExportDto(
                    id = pair.id,
                    qaCode = pair.qaCode,
                    category = pair.category,
                    questionType = pair.questionType,
                    difficulty = pair.difficulty,
                    riskLevel = pair.riskLevel,
                    question = pair.question,
                    expectedAnswer = pair.expectedAnswer,
                    mustInclude = pair.mustInclude,
                    mustNotInclude = pair.mustNotInclude,
                    sourceMustMatch = pair.sourceMustMatch,
                    status = pair.status,
                    notes = pair.notes,
                    sources =
                        sources.map { s ->
                            QaSourceExportDto(
                                policyArticleId = s.policyArticleId,
                                insuranceCompany = s.insuranceCompany,
                                productName = s.productName,
                                articleNumber = s.articleNumber,
                                contentType = s.contentType,
                            )
                        },
                )
            }

        val exportData =
            DatasetExportDto(
                datasetId = dataset.id!!,
                name = dataset.name,
                version = dataset.version,
                qaPairs = exportDtos,
            )

        val file = File(outputPath)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, exportData)
        log.info("JSON export 완료: {} ({}개 QA 쌍)", outputPath, exportDtos.size)
    }

    /** JSON import (수동 리뷰 후) */
    suspend fun importFromJson(inputPath: String) {
        val file = File(inputPath)
        if (!file.exists()) error("파일을 찾을 수 없습니다: $inputPath")

        val importData = objectMapper.readValue(file, DatasetExportDto::class.java)
        var updated = 0

        for (dto in importData.qaPairs) {
            val existing = qaPairRepository.findById(dto.id) ?: continue

            // 상태, 메모, 기대 답변, 키워드가 변경된 경우 업데이트
            if (existing.status != dto.status ||
                existing.notes != dto.notes ||
                existing.expectedAnswer != dto.expectedAnswer ||
                existing.mustInclude != dto.mustInclude ||
                existing.mustNotInclude != dto.mustNotInclude
            ) {
                qaPairRepository.save(
                    existing.copy(
                        status = dto.status,
                        notes = dto.notes,
                        expectedAnswer = dto.expectedAnswer,
                        mustInclude = dto.mustInclude,
                        mustNotInclude = dto.mustNotInclude,
                        updatedAt = Instant.now(),
                    ),
                )
                updated++
            }
        }

        log.info("JSON import 완료: {} ({}개 QA 쌍 업데이트)", inputPath, updated)
    }
}
