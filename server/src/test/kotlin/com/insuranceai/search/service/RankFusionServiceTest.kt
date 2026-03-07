package com.insuranceai.search.service

import com.insuranceai.search.dto.KeywordSearchResult
import com.insuranceai.search.dto.VectorSearchResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RankFusionServiceTest {
    private val service = RankFusionService()

    private val companyId1 = UUID.randomUUID()
    private val companyId2 = UUID.randomUUID()
    private val companyId3 = UUID.randomUUID()

    private fun keywordResult(
        articleId: UUID = UUID.randomUUID(),
        rank: Int,
        companyId: UUID = companyId1,
        companyName: String = "삼성생명",
    ) = KeywordSearchResult(
        articleId = articleId,
        productId = UUID.randomUUID(),
        productName = "테스트 보험",
        companyId = companyId,
        companyName = companyName,
        insuranceType = "LIFE",
        contentType = "COVERAGE",
        articleNumber = "제1조",
        articleTitle = "보장내용",
        content = "테스트 내용",
        dataSource = "ASSOCIATION",
        rank = rank,
    )

    private fun vectorResult(
        articleId: UUID = UUID.randomUUID(),
        similarity: Float = 0.8f,
        companyId: UUID = companyId1,
        companyName: String = "삼성생명",
    ) = VectorSearchResult(
        articleId = articleId,
        productId = UUID.randomUUID(),
        productName = "테스트 보험",
        companyId = companyId,
        companyName = companyName,
        insuranceType = "LIFE",
        contentType = "COVERAGE",
        articleNumber = "제1조",
        articleTitle = "보장내용",
        content = "테스트 내용",
        dataSource = "ASSOCIATION",
        similarity = similarity,
    )

    @Test
    fun `빈 입력은 빈 결과를 반환한다`() {
        val result = service.fuse(emptyList(), emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `키워드 결과만 있을 때 정상 동작한다`() {
        val keyword1 = keywordResult(rank = 1)
        val keyword2 = keywordResult(rank = 2)

        val result = service.fuse(listOf(keyword1, keyword2), emptyList())

        assertEquals(2, result.size)
        // 상위 랭크가 더 높은 점수
        assertTrue(result[0].relevanceScore >= result[1].relevanceScore)
        assertNotNull(result[0].keywordRank)
        assertNull(result[0].vectorRank)
    }

    @Test
    fun `벡터 결과만 있을 때 정상 동작한다`() {
        val vector1 = vectorResult(similarity = 0.9f)
        val vector2 = vectorResult(similarity = 0.7f)

        val result = service.fuse(emptyList(), listOf(vector1, vector2))

        assertEquals(2, result.size)
        assertTrue(result[0].relevanceScore >= result[1].relevanceScore)
        assertNull(result[0].keywordRank)
        assertNotNull(result[0].vectorRank)
    }

    @Test
    fun `동일 articleId가 양쪽에 있으면 점수가 합산된다`() {
        val sharedId = UUID.randomUUID()
        val keyword = keywordResult(articleId = sharedId, rank = 1)
        val vector = vectorResult(articleId = sharedId, similarity = 0.9f)

        val uniqueKeyword = keywordResult(rank = 2)

        val result = service.fuse(listOf(keyword, uniqueKeyword), listOf(vector))

        // 양쪽에 모두 있는 결과가 가장 높은 점수
        assertEquals(sharedId, result[0].articleId)
        assertEquals(1f, result[0].relevanceScore) // max 정규화 = 1.0
        assertNotNull(result[0].keywordRank)
        assertNotNull(result[0].vectorRank)
    }

    @Test
    fun `결과가 relevanceScore 내림차순으로 정렬된다`() {
        val k1 = keywordResult(rank = 1)
        val k2 = keywordResult(rank = 5)
        val v1 = vectorResult(similarity = 0.9f)

        val result = service.fuse(listOf(k1, k2), listOf(v1))

        for (i in 0 until result.size - 1) {
            assertTrue(result[i].relevanceScore >= result[i + 1].relevanceScore)
        }
    }

    @Test
    fun `다양성 보장 - 보험사별 최대 maxPerCompany개`() {
        val sharedId1 = UUID.randomUUID()
        val sharedId2 = UUID.randomUUID()
        val sharedId3 = UUID.randomUUID()
        val sharedId4 = UUID.randomUUID()
        val sharedId5 = UUID.randomUUID()

        val keywords =
            listOf(
                keywordResult(articleId = sharedId1, rank = 1, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId2, rank = 2, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId3, rank = 3, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId4, rank = 4, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId5, rank = 5, companyId = companyId2, companyName = "한화생명"),
            )

        val fused = service.fuse(keywords, emptyList())
        val diverse = service.ensureDiversity(fused, maxPerCompany = 3)

        val company1Count = diverse.count { it.companyId == companyId1 }
        assertEquals(3, company1Count)
        assertEquals(4, diverse.size)
    }

    @Test
    fun `다양성 보장 후에도 relevanceScore 내림차순 정렬된다`() {
        val keywords =
            listOf(
                keywordResult(rank = 1, companyId = companyId1),
                keywordResult(rank = 2, companyId = companyId2),
                keywordResult(rank = 3, companyId = companyId3),
            )

        val fused = service.fuse(keywords, emptyList())
        val diverse = service.ensureDiversity(fused, maxPerCompany = 2)

        for (i in 0 until diverse.size - 1) {
            assertTrue(diverse[i].relevanceScore >= diverse[i + 1].relevanceScore)
        }
    }
}
