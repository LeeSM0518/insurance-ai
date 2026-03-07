package com.insuranceai.chat.service

import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionAnalyzerTest {
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val analyzer = QuestionAnalyzer(insuranceCompanyRepository)

    init {
        // 보험사 목록 수동 로드 (테스트에서는 @EventListener 동작하지 않음)
        every { insuranceCompanyRepository.findByIsActiveTrue() } returns
            flowOf(
                InsuranceCompanyEntity(name = "삼성생명", type = "LIFE"),
                InsuranceCompanyEntity(name = "한화생명", type = "LIFE"),
                InsuranceCompanyEntity(name = "DB손해보험", type = "NON_LIFE"),
            )
        analyzer.loadCompanyNames()
    }

    @Test
    fun `보험사 추출 - 질문에 포함된 보험사를 추출한다`() {
        val companies = analyzer.extractCompanies("삼성생명 암보험 면책사항 알려주세요")
        assertEquals(listOf("삼성생명"), companies)
    }

    @Test
    fun `보험사 추출 - 복수 보험사를 추출한다`() {
        val companies = analyzer.extractCompanies("삼성생명과 한화생명 비교해주세요")
        assertEquals(listOf("삼성생명", "한화생명"), companies)
    }

    @Test
    fun `보험사 추출 - 없으면 빈 리스트`() {
        val companies = analyzer.extractCompanies("암보험 면책사항 알려주세요")
        assertTrue(companies.isEmpty())
    }

    @Test
    fun `카테고리 추출 - 암보험`() {
        val categories = analyzer.extractCategories("암보험 보장내용")
        assertTrue(categories.contains("CANCER"))
    }

    @Test
    fun `카테고리 추출 - 실손보험`() {
        val categories = analyzer.extractCategories("실손보험 가입 조건")
        assertTrue(categories.contains("HEALTH"))
    }

    @Test
    fun `카테고리 추출 - 복수 카테고리`() {
        val categories = analyzer.extractCategories("종신보험과 암보험 차이")
        assertTrue(categories.contains("WHOLE_LIFE"))
        assertTrue(categories.contains("CANCER"))
    }

    @Test
    fun `내용유형 추출 - 면책`() {
        val contentTypes = analyzer.extractContentTypes("면책사항 알려주세요")
        assertTrue(contentTypes.contains("EXEMPTION"))
    }

    @Test
    fun `내용유형 추출 - 감액`() {
        val contentTypes = analyzer.extractContentTypes("감액기간이 어떻게 되나요")
        assertTrue(contentTypes.contains("REDUCTION"))
    }

    @Test
    fun `내용유형 추출 - 보장`() {
        val contentTypes = analyzer.extractContentTypes("보장범위가 어디까지인가요")
        assertTrue(contentTypes.contains("COVERAGE"))
    }

    @Test
    fun `비교 의도 감지`() {
        assertTrue(analyzer.detectComparison("삼성생명 vs 한화생명"))
        assertTrue(analyzer.detectComparison("암보험 비교해주세요"))
        assertTrue(analyzer.detectComparison("차이점이 뭔가요"))
        assertTrue(analyzer.detectComparison("뭐가 달라"))
        assertFalse(analyzer.detectComparison("면책사항 알려주세요"))
    }

    @Test
    fun `보충 데이터 필요 여부 - 해약환급금`() {
        val needs = analyzer.detectSupplementaryNeeds("해약환급금이 얼마인가요")
        assertTrue(needs.surrenderValue)
        assertFalse(needs.premiumExample)
        assertFalse(needs.priceIndex)
    }

    @Test
    fun `보충 데이터 필요 여부 - 보험료`() {
        val needs = analyzer.detectSupplementaryNeeds("월 납입 보험료가 얼마인가요")
        assertTrue(needs.premiumExample)
    }

    @Test
    fun `보충 데이터 필요 여부 - 가격지수`() {
        val needs = analyzer.detectSupplementaryNeeds("가격지수 알려주세요")
        assertTrue(needs.priceIndex)
    }

    @Test
    fun `통합 분석이 올바르게 동작한다`() =
        runTest {
            val analysis = analyzer.analyze("삼성생명 암보험 면책사항 비교해주세요")

            assertEquals(listOf("삼성생명"), analysis.companies)
            assertTrue(analysis.categories.contains("CANCER"))
            assertTrue(analysis.contentTypes.contains("EXEMPTION"))
            assertTrue(analysis.isComparison)
        }
}
