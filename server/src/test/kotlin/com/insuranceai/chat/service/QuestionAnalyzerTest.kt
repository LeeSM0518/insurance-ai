package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionAnalyzerTest {
    private val suspendingChatClient = mockk<SuspendingChatClient>()
    private val promptLoader = mockk<PromptLoader>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val analyzer = QuestionAnalyzer(suspendingChatClient, promptLoader, insuranceCompanyRepository)

    init {
        every { insuranceCompanyRepository.findByIsActiveTrue() } returns
            flowOf(
                InsuranceCompanyEntity(name = "삼성생명", type = "LIFE"),
                InsuranceCompanyEntity(name = "한화생명", type = "LIFE"),
                InsuranceCompanyEntity(name = "DB손해보험", type = "NON_LIFE"),
            )
        analyzer.loadCompanyNames()

        every { promptLoader.render(any(), any()) } returns "시스템 프롬프트"
    }

    @Test
    fun `LLM 호출로 질문 분석 결과를 반환한다`() =
        runTest {
            val expected =
                QuestionAnalysis(
                    companies = listOf("삼성생명"),
                    categories = listOf("CANCER"),
                    contentTypes = listOf("EXEMPTION"),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )
            coEvery {
                suspendingChatClient.callEntity(
                    userMessage = any(),
                    systemMessage = any(),
                    responseType = QuestionAnalysis::class.java,
                )
            } returns expected

            val result = analyzer.analyze("삼성생명 암보험 면책사항 알려주세요")

            assertEquals(listOf("삼성생명"), result.companies)
            assertTrue(result.categories.contains("CANCER"))
            assertTrue(result.contentTypes.contains("EXEMPTION"))
            assertFalse(result.isComparison)
        }

    @Test
    fun `비교 질의를 감지한다`() =
        runTest {
            val expected =
                QuestionAnalysis(
                    companies = listOf("삼성생명", "한화생명"),
                    categories = listOf("CANCER"),
                    contentTypes = emptyList(),
                    isComparison = true,
                    needsSupplementary = SupplementaryNeeds(),
                )
            coEvery {
                suspendingChatClient.callEntity(
                    userMessage = any(),
                    systemMessage = any(),
                    responseType = QuestionAnalysis::class.java,
                )
            } returns expected

            val result = analyzer.analyze("삼성생명 한화생명 암보험 비교해주세요")

            assertTrue(result.isComparison)
            assertEquals(2, result.companies.size)
        }

    @Test
    fun `보충 데이터 필요 여부를 감지한다`() =
        runTest {
            val expected =
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = emptyList(),
                    contentTypes = listOf("TERMINATION"),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(surrenderValue = true),
                )
            coEvery {
                suspendingChatClient.callEntity(
                    userMessage = any(),
                    systemMessage = any(),
                    responseType = QuestionAnalysis::class.java,
                )
            } returns expected

            val result = analyzer.analyze("해약환급금이 얼마인가요")

            assertTrue(result.needsSupplementary.surrenderValue)
            assertFalse(result.needsSupplementary.premiumExample)
        }

    @Test
    fun `LLM 호출 실패 시 기본값을 반환한다`() =
        runTest {
            coEvery {
                suspendingChatClient.callEntity(
                    userMessage = any(),
                    systemMessage = any(),
                    responseType = QuestionAnalysis::class.java,
                )
            } throws RuntimeException("LLM 호출 실패")

            val result = analyzer.analyze("삼성생명 암보험")

            assertTrue(result.companies.isEmpty())
            assertTrue(result.categories.isEmpty())
            assertTrue(result.contentTypes.isEmpty())
            assertFalse(result.isComparison)
            assertFalse(result.needsSupplementary.surrenderValue)
        }

    @Test
    fun `프롬프트에 보험사 목록이 주입된다`() =
        runTest {
            coEvery {
                suspendingChatClient.callEntity(
                    userMessage = any(),
                    systemMessage = any(),
                    responseType = QuestionAnalysis::class.java,
                )
            } returns
                QuestionAnalysis(
                    companies = emptyList(),
                    categories = emptyList(),
                    contentTypes = emptyList(),
                    isComparison = false,
                    needsSupplementary = SupplementaryNeeds(),
                )

            analyzer.analyze("테스트 질문")

            verify {
                promptLoader.render(
                    "chat/question-analysis.st",
                    mapOf("companyNames" to "삼성생명, 한화생명, DB손해보험"),
                )
            }
        }
}
