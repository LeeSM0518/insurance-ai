package com.insuranceai.evaluation.service

import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import com.insuranceai.evaluation.entity.GoldenQaSourceEntity
import org.springframework.stereotype.Service
import java.util.UUID

data class RuleBasedEvalResult(
    val mustIncludePass: Boolean,
    val mustNotIncludePass: Boolean,
    val sourceMatchPass: Boolean,
    val mustIncludeDetails: List<String>,
    val mustNotIncludeDetails: List<String>,
    val sourceMatchDetails: List<String>,
)

@Service
class RuleBasedEvalService {
    /** 규칙 기반 생성 품질 평가 */
    fun evaluate(
        qaPair: GoldenQaPairEntity,
        actualAnswer: String,
        retrievedArticleIds: List<UUID>,
        groundTruthSources: List<GoldenQaSourceEntity>,
    ): RuleBasedEvalResult {
        // 1. must_include 체크
        val mustIncludeDetails = mutableListOf<String>()
        var mustIncludePass = true
        for (keyword in qaPair.mustInclude) {
            if (!actualAnswer.contains(keyword, ignoreCase = true)) {
                mustIncludePass = false
                mustIncludeDetails.add("누락: '$keyword'")
            }
        }

        // 2. must_not_include 체크
        val mustNotIncludeDetails = mutableListOf<String>()
        var mustNotIncludePass = true
        for (keyword in qaPair.mustNotInclude) {
            if (actualAnswer.contains(keyword, ignoreCase = true)) {
                mustNotIncludePass = false
                mustNotIncludeDetails.add("포함됨: '$keyword'")
            }
        }

        // 3. 출처 매칭 체크
        val sourceMatchDetails = mutableListOf<String>()
        var sourceMatchPass = true
        if (qaPair.sourceMustMatch && groundTruthSources.isNotEmpty()) {
            for (source in groundTruthSources) {
                if (source.policyArticleId !in retrievedArticleIds) {
                    sourceMatchPass = false
                    sourceMatchDetails.add("미검색: ${source.insuranceCompany} ${source.articleNumber ?: ""}")
                }
            }
        }

        return RuleBasedEvalResult(
            mustIncludePass = mustIncludePass,
            mustNotIncludePass = mustNotIncludePass,
            sourceMatchPass = sourceMatchPass,
            mustIncludeDetails = mustIncludeDetails,
            mustNotIncludeDetails = mustNotIncludeDetails,
            sourceMatchDetails = sourceMatchDetails,
        )
    }
}
