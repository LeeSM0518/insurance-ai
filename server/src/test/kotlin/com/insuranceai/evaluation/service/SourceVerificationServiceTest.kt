package com.insuranceai.evaluation.service

import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceVerificationServiceTest {
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val service = SourceVerificationService(insuranceCompanyRepository)

    @Test
    fun `조문번호 패턴 추출 - 제N조`() {
        val result = service.extractArticleNumbers("제5조에 따르면 면책됩니다")
        assertEquals(setOf("제5조"), result)
    }

    @Test
    fun `조문번호 패턴 추출 - 제N조의N`() {
        val result = service.extractArticleNumbers("제12조의2 규정에 따라")
        assertEquals(setOf("제12조의2"), result)
    }

    @Test
    fun `조문번호 복수 추출`() {
        val result = service.extractArticleNumbers("제3조와 제5조에 따르면")
        assertEquals(setOf("제3조", "제5조"), result)
    }

    @Test
    fun `조문번호가 없으면 빈 셋`() {
        val result = service.extractArticleNumbers("면책사항에 해당합니다")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `보험사명 추출 - DB에 등록된 보험사`() =
        runTest {
            coEvery { insuranceCompanyRepository.findAll() } returns
                flowOf(
                    InsuranceCompanyEntity(
                        id = UUID.randomUUID(),
                        name = "삼성생명",
                        type = "LIFE",
                        aliases = listOf("삼성"),
                    ),
                    InsuranceCompanyEntity(
                        id = UUID.randomUUID(),
                        name = "한화생명",
                        type = "LIFE",
                        aliases = emptyList(),
                    ),
                )

            val result = service.extractCompanyNames("삼성생명의 종신보험에 따르면")
            assertEquals(setOf("삼성생명"), result)
        }

    @Test
    fun `보험사명 추출 - 별명으로도 매칭`() =
        runTest {
            coEvery { insuranceCompanyRepository.findAll() } returns
                flowOf(
                    InsuranceCompanyEntity(
                        id = UUID.randomUUID(),
                        name = "삼성생명",
                        type = "LIFE",
                        aliases = listOf("삼성"),
                    ),
                )

            val result = service.extractCompanyNames("삼성의 보험 상품")
            assertEquals(setOf("삼성생명"), result)
        }
}
