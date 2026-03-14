package com.insuranceai.evaluation.service

import com.insuranceai.evaluation.domain.QaPairStatus
import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import com.insuranceai.evaluation.repository.GoldenQaPairRepository
import com.insuranceai.evaluation.repository.GoldenQaSourceRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class ValidationResult(
    val qaCode: String,
    val passed: Boolean,
    val errors: List<String>,
)

@Service
class GoldenQaValidationService(
    private val qaPairRepository: GoldenQaPairRepository,
    private val qaSourceRepository: GoldenQaSourceRepository,
    private val policyArticleRepository: PolicyArticleRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 자동 검증 + Gold 승격 (limit=null이면 전체) */
    suspend fun validateAndPromote(
        datasetId: UUID,
        limit: Int? = null,
    ): List<ValidationResult> {
        val allPairs = qaPairRepository.findByDatasetIdAndStatus(datasetId, QaPairStatus.SILVER.name).toList()
        val qaPairs = if (limit != null) allPairs.take(limit) else allPairs
        val results = mutableListOf<ValidationResult>()

        log.info("검증 대상: {}개 (전체 SILVER: {}개)", qaPairs.size, allPairs.size)

        for (pair in qaPairs) {
            val result = validateSinglePair(pair)
            results.add(result)

            if (result.passed) {
                qaPairRepository.save(
                    pair.copy(
                        status = QaPairStatus.GOLD.name,
                        updatedAt = Instant.now(),
                    ),
                )
            }
        }

        val passedCount = results.count { it.passed }
        val failedCount = results.count { !it.passed }
        log.info("자동 검증 완료: 통과 {}개, 실패 {}개 (총 {}개)", passedCount, failedCount, results.size)

        return results
    }

    /** 단일 QA 쌍 검증 */
    suspend fun validateSinglePair(pair: GoldenQaPairEntity): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. 질문 길이 검증 (10자 이상)
        if (pair.question.length < 10) {
            errors.add("질문이 너무 짧습니다 (${pair.question.length}자, 최소 10자)")
        }

        // 2. 답변 길이 검증 (30자 이상)
        if (pair.expectedAnswer.length < 30) {
            errors.add("기대 답변이 너무 짧습니다 (${pair.expectedAnswer.length}자, 최소 30자)")
        }

        // 3. must_include 키워드가 기대 답변에 포함되는지 확인
        for (keyword in pair.mustInclude) {
            if (!pair.expectedAnswer.contains(keyword, ignoreCase = true)) {
                errors.add("must_include 키워드 '$keyword'이 기대 답변에 없습니다")
            }
        }

        // 4. must_not_include 키워드가 기대 답변에 포함되지 않는지 확인
        for (keyword in pair.mustNotInclude) {
            if (pair.expectedAnswer.contains(keyword, ignoreCase = true)) {
                errors.add("must_not_include 키워드 '$keyword'이 기대 답변에 포함되어 있습니다")
            }
        }

        // 5. 출처 FK 존재 확인 (거절 질문은 출처 없음)
        if (pair.sourceMustMatch) {
            val sources = qaSourceRepository.findByQaPairId(pair.id!!).toList()
            if (sources.isEmpty()) {
                errors.add("출처 정보가 없습니다")
            }

            for (source in sources) {
                val article = policyArticleRepository.findById(source.policyArticleId)
                if (article == null) {
                    errors.add("출처 article_id가 DB에 존재하지 않습니다: ${source.policyArticleId}")
                }
            }
        }

        return ValidationResult(
            qaCode = pair.qaCode,
            passed = errors.isEmpty(),
            errors = errors,
        )
    }
}
