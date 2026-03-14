package com.insuranceai.evaluation.service

import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import com.insuranceai.evaluation.entity.GoldenQaSourceEntity
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleBasedEvalServiceTest {
    private val service = RuleBasedEvalService()

    private val datasetId = UUID.randomUUID()
    private val articleId = UUID.randomUUID()

    private fun createQaPair(
        mustInclude: List<String> = emptyList(),
        mustNotInclude: List<String> = emptyList(),
        sourceMustMatch: Boolean = true,
    ) = GoldenQaPairEntity(
        id = UUID.randomUUID(),
        datasetId = datasetId,
        qaCode = "GD-001",
        category = "COVERAGE",
        questionType = "SINGLE_LOOKUP",
        difficulty = "EASY",
        riskLevel = "MEDIUM",
        question = "테스트 질문",
        expectedAnswer = "테스트 답변",
        mustInclude = mustInclude,
        mustNotInclude = mustNotInclude,
        sourceMustMatch = sourceMustMatch,
    )

    private fun createSource() =
        GoldenQaSourceEntity(
            id = UUID.randomUUID(),
            qaPairId = UUID.randomUUID(),
            policyArticleId = articleId,
            insuranceCompany = "삼성생명",
            productName = "종신보험",
        )

    @Test
    fun `must_include 키워드가 모두 포함되면 통과`() {
        val pair = createQaPair(mustInclude = listOf("90일", "면책"))
        val result = service.evaluate(pair, "이 보험의 면책기간은 90일입니다.", listOf(articleId), listOf(createSource()))

        assertTrue(result.mustIncludePass)
    }

    @Test
    fun `must_include 키워드가 누락되면 실패`() {
        val pair = createQaPair(mustInclude = listOf("90일", "면책"))
        val result = service.evaluate(pair, "이 보험의 보장기간은 10년입니다.", listOf(articleId), listOf(createSource()))

        assertFalse(result.mustIncludePass)
        assertTrue(result.mustIncludeDetails.isNotEmpty())
    }

    @Test
    fun `must_not_include 키워드가 없으면 통과`() {
        val pair = createQaPair(mustNotInclude = listOf("추측", "일반적으로"))
        val result = service.evaluate(pair, "약관에 따르면 보장됩니다.", listOf(articleId), listOf(createSource()))

        assertTrue(result.mustNotIncludePass)
    }

    @Test
    fun `must_not_include 키워드가 포함되면 실패`() {
        val pair = createQaPair(mustNotInclude = listOf("추측", "일반적으로"))
        val result = service.evaluate(pair, "일반적으로 보장됩니다.", listOf(articleId), listOf(createSource()))

        assertFalse(result.mustNotIncludePass)
    }

    @Test
    fun `출처가 검색 결과에 포함되면 통과`() {
        val pair = createQaPair(sourceMustMatch = true)
        val result = service.evaluate(pair, "답변", listOf(articleId), listOf(createSource()))

        assertTrue(result.sourceMatchPass)
    }

    @Test
    fun `출처가 검색 결과에 없으면 실패`() {
        val pair = createQaPair(sourceMustMatch = true)
        val otherArticleId = UUID.randomUUID()
        val result = service.evaluate(pair, "답변", listOf(otherArticleId), listOf(createSource()))

        assertFalse(result.sourceMatchPass)
    }

    @Test
    fun `sourceMustMatch가 false이면 출처 체크 생략`() {
        val pair = createQaPair(sourceMustMatch = false)
        val result = service.evaluate(pair, "답변", emptyList(), emptyList())

        assertTrue(result.sourceMatchPass)
    }
}
