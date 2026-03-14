package com.insuranceai.evaluation.service

import com.insuranceai.evaluation.entity.GoldenQaPairEntity
import com.insuranceai.evaluation.entity.GoldenQaSourceEntity
import com.insuranceai.evaluation.repository.GoldenQaPairRepository
import com.insuranceai.evaluation.repository.GoldenQaSourceRepository
import com.insuranceai.policy.entity.PolicyArticleEntity
import com.insuranceai.policy.repository.PolicyArticleRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoldenQaValidationServiceTest {
    private val qaPairRepository = mockk<GoldenQaPairRepository>()
    private val qaSourceRepository = mockk<GoldenQaSourceRepository>()
    private val policyArticleRepository = mockk<PolicyArticleRepository>()

    private val service =
        GoldenQaValidationService(
            qaPairRepository,
            qaSourceRepository,
            policyArticleRepository,
        )

    private val datasetId = UUID.randomUUID()
    private val articleId = UUID.randomUUID()
    private val qaPairId = UUID.randomUUID()

    @Test
    fun `정상 QA 쌍이면 검증 통과`() =
        runTest {
            val pair = createValidPair()
            setupSourceMocks(pair)

            val result = service.validateSinglePair(pair)

            assertTrue(result.passed)
            assertTrue(result.errors.isEmpty())
        }

    @Test
    fun `질문이 10자 미만이면 실패`() =
        runTest {
            val pair = createValidPair().copy(question = "짧은 질문")
            setupSourceMocks(pair)

            val result = service.validateSinglePair(pair)

            assertFalse(result.passed)
            assertTrue(result.errors.any { it.contains("질문이 너무 짧습니다") })
        }

    @Test
    fun `기대 답변이 30자 미만이면 실패`() =
        runTest {
            val pair = createValidPair().copy(expectedAnswer = "짧은 답변입니다")
            setupSourceMocks(pair)

            val result = service.validateSinglePair(pair)

            assertFalse(result.passed)
            assertTrue(result.errors.any { it.contains("기대 답변이 너무 짧습니다") })
        }

    @Test
    fun `must_include 키워드가 답변에 없으면 실패`() =
        runTest {
            val pair =
                createValidPair().copy(
                    mustInclude = listOf("90일", "면책"),
                    expectedAnswer = "이 보험의 보장기간은 10년입니다. 약관 제5조에 따르면 보장됩니다.",
                )
            setupSourceMocks(pair)

            val result = service.validateSinglePair(pair)

            assertFalse(result.passed)
            assertTrue(result.errors.any { it.contains("must_include") })
        }

    @Test
    fun `출처가 DB에 존재하지 않으면 실패`() =
        runTest {
            val pair = createValidPair()
            val source =
                GoldenQaSourceEntity(
                    id = UUID.randomUUID(),
                    qaPairId = pair.id!!,
                    policyArticleId = articleId,
                    insuranceCompany = "삼성생명",
                    productName = "종신보험",
                )
            coEvery { qaSourceRepository.findByQaPairId(pair.id!!) } returns flowOf(source)
            coEvery { policyArticleRepository.findById(articleId) } returns null

            val result = service.validateSinglePair(pair)

            assertFalse(result.passed)
            assertTrue(result.errors.any { it.contains("DB에 존재하지 않습니다") })
        }

    @Test
    fun `sourceMustMatch가 false이면 출처 검증 생략`() =
        runTest {
            val pair = createValidPair().copy(sourceMustMatch = false)

            val result = service.validateSinglePair(pair)

            assertTrue(result.passed)
        }

    private fun createValidPair() =
        GoldenQaPairEntity(
            id = qaPairId,
            datasetId = datasetId,
            qaCode = "GD-001",
            category = "COVERAGE",
            questionType = "SINGLE_LOOKUP",
            difficulty = "EASY",
            riskLevel = "MEDIUM",
            question = "삼성생명 종신보험의 보장 범위는 어떻게 되나요?",
            expectedAnswer = "삼성생명 종신보험은 약관 제5조에 따르면 사망 시 보험금을 지급합니다. 보장 범위에는 일반사망과 재해사망이 포함됩니다.",
            mustInclude = listOf("사망", "보험금"),
            mustNotInclude = listOf("추측"),
            sourceMustMatch = true,
        )

    private fun setupSourceMocks(pair: GoldenQaPairEntity) {
        val source =
            GoldenQaSourceEntity(
                id = UUID.randomUUID(),
                qaPairId = pair.id!!,
                policyArticleId = articleId,
                insuranceCompany = "삼성생명",
                productName = "종신보험",
            )
        coEvery { qaSourceRepository.findByQaPairId(pair.id!!) } returns flowOf(source)
        coEvery { policyArticleRepository.findById(articleId) } returns
            PolicyArticleEntity(
                id = articleId,
                productId = UUID.randomUUID(),
                content = "면책사항 내용",
                contentType = "COVERAGE",
            )
    }
}
