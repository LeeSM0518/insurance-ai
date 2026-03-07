package com.insuranceai.chat.service

import com.insuranceai.chat.dto.QuestionAnalysis
import com.insuranceai.chat.dto.SupplementaryNeeds
import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.policy.entity.SurrenderValueEntity
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import com.insuranceai.search.dto.FusedSearchResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextBuilderTest {
    private val surrenderValueRepository = mockk<SurrenderValueRepository>()
    private val premiumExampleRepository = mockk<PremiumExampleRepository>()
    private val productIndexRepository = mockk<ProductIndexRepository>()

    private val builder = ContextBuilder(surrenderValueRepository, premiumExampleRepository, productIndexRepository)

    private val productId = UUID.randomUUID()

    private fun fusedResult(
        articleId: UUID = UUID.randomUUID(),
        productId: UUID = this.productId,
        companyName: String = "삼성생명",
        content: String = "면책사항에 해당하는 경우 보험금을 지급하지 않습니다",
    ) = FusedSearchResult(
        articleId = articleId,
        productId = productId,
        productName = "테스트 보험",
        companyId = UUID.randomUUID(),
        companyName = companyName,
        insuranceType = "LIFE",
        contentType = "COVERAGE",
        articleNumber = "제1조",
        articleTitle = "보장내용",
        content = content,
        dataSource = "ASSOCIATION",
        relevanceScore = 0.8f,
        keywordRank = 1,
        vectorRank = 2,
    )

    private val defaultAnalysis =
        QuestionAnalysis(
            companies = listOf("삼성생명"),
            categories = listOf("CANCER"),
            contentTypes = listOf("COVERAGE"),
            isComparison = false,
            needsSupplementary = SupplementaryNeeds(),
        )

    @Test
    fun `빈 검색 결과로 컨텍스트를 구성한다`() =
        runTest {
            val result = builder.build(emptyList(), defaultAnalysis, emptyList())
            assertTrue(result.sources.isEmpty())
        }

    @Test
    fun `검색 결과로 약관 자료 섹션이 구성된다`() =
        runTest {
            every { surrenderValueRepository.findByProductId(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductId(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductId(any()) } returns emptyFlow()

            val searchResults = listOf(fusedResult())
            val result = builder.build(searchResults, defaultAnalysis, emptyList())

            assertTrue(result.contextText.contains("## 검색된 약관 자료"))
            assertTrue(result.contextText.contains("[출처 1]"))
            assertTrue(result.contextText.contains("삼성생명"))
            assertEquals(1, result.sources.size)
        }

    @Test
    fun `보충 데이터가 포함된다`() =
        runTest {
            val analysisWithSupp =
                defaultAnalysis.copy(
                    needsSupplementary = SupplementaryNeeds(surrenderValue = true),
                )

            every { surrenderValueRepository.findByProductId(productId) } returns
                flowOf(
                    SurrenderValueEntity(
                        productId = productId,
                        elapsedMonths = 12,
                        premiumPaid = 100000,
                        surrenderValue = 50000,
                        surrenderRate = BigDecimal("50.0"),
                    ),
                )
            every { premiumExampleRepository.findByProductId(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductId(any()) } returns emptyFlow()

            val result = builder.build(listOf(fusedResult()), analysisWithSupp, emptyList())

            assertTrue(result.contextText.contains("## 보충 정형 데이터"))
            assertTrue(result.contextText.contains("해약환급금"))
        }

    @Test
    fun `대화 이력이 포함된다`() =
        runTest {
            every { surrenderValueRepository.findByProductId(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductId(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductId(any()) } returns emptyFlow()

            val messages =
                listOf(
                    ChatMessageEntity(chatSessionId = UUID.randomUUID(), role = "USER", content = "면책사항 알려주세요"),
                    ChatMessageEntity(chatSessionId = UUID.randomUUID(), role = "ASSISTANT", content = "면책사항은 다음과 같습니다..."),
                )

            val result = builder.build(listOf(fusedResult()), defaultAnalysis, messages)

            assertTrue(result.contextText.contains("## 대화 이력"))
            assertTrue(result.contextText.contains("USER: 면책사항 알려주세요"))
            assertTrue(result.contextText.contains("ASSISTANT: 면책사항은 다음과 같습니다..."))
        }

    @Test
    fun `토큰 추정이 올바르게 계산된다`() {
        val tokens = builder.estimateTokens("가나다") // 3글자 * 1.5 = 4
        assertEquals(4, tokens)
    }

    @Test
    fun `출처 정보가 올바르게 구성된다`() =
        runTest {
            every { surrenderValueRepository.findByProductId(any()) } returns emptyFlow()
            every { premiumExampleRepository.findByProductId(any()) } returns emptyFlow()
            every { productIndexRepository.findByProductId(any()) } returns emptyFlow()

            val result = builder.build(listOf(fusedResult()), defaultAnalysis, emptyList())

            val source = result.sources[0]
            assertEquals("삼성생명", source.insuranceCompany)
            assertEquals("테스트 보험", source.productName)
            assertEquals("제1조", source.articleNumber)
            assertEquals(0.8f, source.relevanceScore)
        }
}
