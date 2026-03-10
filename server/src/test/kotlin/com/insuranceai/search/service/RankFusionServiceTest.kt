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
        matchQuality: Float = 0.5f,
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
        matchQuality = matchQuality,
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
        val keyword1 = keywordResult(rank = 1, matchQuality = 0.7f)
        val keyword2 = keywordResult(rank = 2, matchQuality = 0.5f)

        val result = service.fuse(listOf(keyword1, keyword2), emptyList())

        assertEquals(2, result.size)
        assertTrue(result[0].relevanceScore >= result[1].relevanceScore)
        assertNotNull(result[0].keywordRank)
        assertNull(result[0].vectorRank)
        // 한쪽만 매칭: SINGLE_MATCH_SCALE(0.8) × 정규화 점수
        assertEquals(RankFusionService.SINGLE_MATCH_SCALE, result[0].relevanceScore, 0.001f)
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
        // 한쪽만 매칭: SINGLE_MATCH_SCALE(0.8) × 정규화 점수
        assertEquals(RankFusionService.SINGLE_MATCH_SCALE, result[0].relevanceScore, 0.001f)
    }

    @Test
    fun `동일 articleId가 양쪽에 있으면 점수가 합산된다`() {
        val sharedId = UUID.randomUUID()
        val keyword = keywordResult(articleId = sharedId, rank = 1, matchQuality = 0.7f)
        val vector = vectorResult(articleId = sharedId, similarity = 0.9f)

        val uniqueKeyword = keywordResult(rank = 2, matchQuality = 0.3f)

        val result = service.fuse(listOf(keyword, uniqueKeyword), listOf(vector))

        // 양쪽에 모두 있는 결과가 가장 높은 점수
        assertEquals(sharedId, result[0].articleId)
        assertNotNull(result[0].keywordRank)
        assertNotNull(result[0].vectorRank)
        // 양쪽 매칭은 한쪽만 매칭보다 항상 높은 점수
        assertTrue(result[0].relevanceScore > result[1].relevanceScore)
    }

    @Test
    fun `양쪽 매칭 결과는 동일 정규화 점수일 때 한쪽만 매칭보다 높은 점수`() {
        val sharedId = UUID.randomUUID()
        val keywordOnlyId = UUID.randomUUID()
        val vectorOnlyId = UUID.randomUUID()

        // shared가 양쪽 retriever에서 최고 점수
        val keywords =
            listOf(
                keywordResult(articleId = sharedId, rank = 1, matchQuality = 0.9f),
                keywordResult(articleId = keywordOnlyId, rank = 2, matchQuality = 0.7f),
            )
        val vectors =
            listOf(
                vectorResult(articleId = sharedId, similarity = 0.95f),
                vectorResult(articleId = vectorOnlyId, similarity = 0.8f),
            )

        val result = service.fuse(keywords, vectors)
        val sharedResult = result.first { it.articleId == sharedId }
        val keywordOnly = result.first { it.articleId == keywordOnlyId }
        val vectorOnly = result.first { it.articleId == vectorOnlyId }

        // 양쪽 매칭 결과가 한쪽만 매칭 결과보다 높은 점수
        assertTrue(sharedResult.relevanceScore > keywordOnly.relevanceScore)
        assertTrue(sharedResult.relevanceScore > vectorOnly.relevanceScore)
        // 양쪽 매칭: Convex Combination (alpha=0.5 × 1.0 + 0.5 × 1.0 = 1.0)
        assertEquals(1.0f, sharedResult.relevanceScore, 0.001f)
    }

    @Test
    fun `alpha=1이면 벡터 점수만, alpha=0이면 키워드 점수만 반영`() {
        val sharedId = UUID.randomUUID()
        val keyword = keywordResult(articleId = sharedId, rank = 1, matchQuality = 0.7f)
        val vector = vectorResult(articleId = sharedId, similarity = 0.9f)

        // alpha=1: 벡터만 반영
        val vectorOnly = service.fuse(listOf(keyword), listOf(vector), alpha = 1.0f)
        assertNotNull(vectorOnly[0].vectorNormalizedScore)
        // alpha=1이면 키워드 점수 기여가 0
        assertEquals(vectorOnly[0].vectorNormalizedScore!!, vectorOnly[0].relevanceScore, 0.001f)

        // alpha=0: 키워드만 반영
        val keywordOnly = service.fuse(listOf(keyword), listOf(vector), alpha = 0.0f)
        assertNotNull(keywordOnly[0].keywordNormalizedScore)
        assertEquals(keywordOnly[0].keywordNormalizedScore!!, keywordOnly[0].relevanceScore, 0.001f)
    }

    @Test
    fun `벡터 점수와 키워드 점수가 각각 독립적으로 0-1 정규화된다`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        val keywords =
            listOf(
                keywordResult(articleId = id1, rank = 1, matchQuality = 0.9f),
                keywordResult(articleId = id2, rank = 2, matchQuality = 0.3f),
            )
        val vectors =
            listOf(
                vectorResult(articleId = id1, similarity = 0.95f),
                vectorResult(articleId = id2, similarity = 0.60f),
            )

        val result = service.fuse(keywords, vectors)

        // 각 retriever에서 최고 점수는 정규화 후 1.0
        val topResult = result.first { it.articleId == id1 }
        assertEquals(1.0f, topResult.keywordNormalizedScore!!, 0.001f)
        assertEquals(1.0f, topResult.vectorNormalizedScore!!, 0.001f)

        // 각 retriever에서 최저 점수는 정규화 후 0.0
        val bottomResult = result.first { it.articleId == id2 }
        assertEquals(0.0f, bottomResult.keywordNormalizedScore!!, 0.001f)
        assertEquals(0.0f, bottomResult.vectorNormalizedScore!!, 0.001f)
    }

    @Test
    fun `한쪽만 매칭 시 SINGLE_MATCH_SCALE로 양쪽 매칭보다 낮은 점수`() {
        val bothId = UUID.randomUUID()
        val keywordOnlyId = UUID.randomUUID()

        // bothId는 양쪽 매칭 (fusion 전 점수 = 1.0), keywordOnlyId는 키워드만 매칭 (fusion 전 점수 = 0.8)
        val keywords =
            listOf(
                keywordResult(articleId = bothId, rank = 1, matchQuality = 0.9f),
                keywordResult(articleId = keywordOnlyId, rank = 2, matchQuality = 0.9f),
            )
        val vectors = listOf(vectorResult(articleId = bothId, similarity = 0.9f))

        val result = service.fuse(keywords, vectors, alpha = 0.5f)

        val bothMatch = result.first { it.articleId == bothId }
        val keywordOnly = result.first { it.articleId == keywordOnlyId }

        // 양쪽 매칭 top이 한쪽만 매칭보다 높음
        assertTrue(bothMatch.relevanceScore > keywordOnly.relevanceScore)
        assertTrue(keywordOnly.relevanceScore < 1.0f)
    }

    @Test
    fun `제목 매칭 결과가 본문만 매칭 결과보다 높은 keywordNorm 점수`() {
        val titleMatchId = UUID.randomUUID()
        val bodyOnlyId = UUID.randomUUID()

        // 제목 매칭: matchQuality가 높음 (0.4 제목 + 0.3 본문 기본)
        val keywords =
            listOf(
                keywordResult(articleId = titleMatchId, rank = 1, matchQuality = 0.7f),
                keywordResult(articleId = bodyOnlyId, rank = 2, matchQuality = 0.35f),
            )

        val result = service.fuse(keywords, emptyList())
        val titleMatch = result.first { it.articleId == titleMatchId }
        val bodyOnly = result.first { it.articleId == bodyOnlyId }

        assertTrue(titleMatch.keywordNormalizedScore!! > bodyOnly.keywordNormalizedScore!!)
    }

    @Test
    fun `양쪽 매칭 top 결과가 한쪽만 매칭 top 결과보다 높은 점수`() {
        val bothMatchId = UUID.randomUUID()
        val keywordOnlyId = UUID.randomUUID()
        val vectorOnlyId = UUID.randomUUID()

        val keywords =
            listOf(
                keywordResult(articleId = bothMatchId, rank = 1, matchQuality = 0.9f),
                keywordResult(articleId = keywordOnlyId, rank = 2, matchQuality = 0.85f),
            )
        val vectors =
            listOf(
                vectorResult(articleId = bothMatchId, similarity = 0.95f),
                vectorResult(articleId = vectorOnlyId, similarity = 0.9f),
            )

        val result = service.fuse(keywords, vectors)
        val bothMatch = result.first { it.articleId == bothMatchId }
        val keywordOnly = result.first { it.articleId == keywordOnlyId }
        val vectorOnly = result.first { it.articleId == vectorOnlyId }

        assertTrue(bothMatch.relevanceScore > keywordOnly.relevanceScore)
        assertTrue(bothMatch.relevanceScore > vectorOnly.relevanceScore)
    }

    @Test
    fun `양쪽 매칭 시 Convex Combination이 적용된다`() {
        val sharedId = UUID.randomUUID()

        // 두 개의 양쪽 매칭 결과로 정규화 범위 확보
        val sharedId2 = UUID.randomUUID()
        val keywords =
            listOf(
                keywordResult(articleId = sharedId, rank = 1, matchQuality = 0.9f),
                keywordResult(articleId = sharedId2, rank = 2, matchQuality = 0.5f),
            )
        val vectors =
            listOf(
                vectorResult(articleId = sharedId, similarity = 0.95f),
                vectorResult(articleId = sharedId2, similarity = 0.7f),
            )

        val alpha = 0.6f
        val result = service.fuse(keywords, vectors, alpha = alpha)
        val topResult = result.first { it.articleId == sharedId }

        // 양쪽 모두 top이므로 정규화 점수 = 1.0
        // Convex Combination: 0.6 × 1.0 + 0.4 × 1.0 = 1.0
        assertEquals(1.0f, topResult.relevanceScore, 0.001f)
    }

    @Test
    fun `alpha 값에 관계없이 한쪽만 매칭 시 양쪽 매칭 대비 동일한 상대 점수`() {
        val bothId = UUID.randomUUID()
        val keywordOnlyId = UUID.randomUUID()

        val keywords =
            listOf(
                keywordResult(articleId = bothId, rank = 1, matchQuality = 0.9f),
                keywordResult(articleId = keywordOnlyId, rank = 2, matchQuality = 0.9f),
            )
        val vectors = listOf(vectorResult(articleId = bothId, similarity = 0.9f))

        // alpha=0.3과 alpha=0.7에서 한쪽만 매칭 결과의 상대 점수 비교
        val result03 = service.fuse(keywords, vectors, alpha = 0.3f)
        val result07 = service.fuse(keywords, vectors, alpha = 0.7f)

        val keywordOnly03 = result03.first { it.articleId == keywordOnlyId }
        val keywordOnly07 = result07.first { it.articleId == keywordOnlyId }

        // 한쪽만 매칭은 SINGLE_MATCH_SCALE 적용이라 alpha와 무관하게 동일한 점수
        assertEquals(keywordOnly03.relevanceScore, keywordOnly07.relevanceScore, 0.001f)
    }

    @Test
    fun `결과가 relevanceScore 내림차순으로 정렬된다`() {
        val k1 = keywordResult(rank = 1, matchQuality = 0.8f)
        val k2 = keywordResult(rank = 5, matchQuality = 0.3f)
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
                keywordResult(articleId = sharedId1, rank = 1, matchQuality = 0.9f, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId2, rank = 2, matchQuality = 0.8f, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId3, rank = 3, matchQuality = 0.7f, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId4, rank = 4, matchQuality = 0.6f, companyId = companyId1, companyName = "삼성생명"),
                keywordResult(articleId = sharedId5, rank = 5, matchQuality = 0.5f, companyId = companyId2, companyName = "한화생명"),
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
                keywordResult(rank = 1, matchQuality = 0.9f, companyId = companyId1),
                keywordResult(rank = 2, matchQuality = 0.7f, companyId = companyId2),
                keywordResult(rank = 3, matchQuality = 0.5f, companyId = companyId3),
            )

        val fused = service.fuse(keywords, emptyList())
        val diverse = service.ensureDiversity(fused, maxPerCompany = 2)

        for (i in 0 until diverse.size - 1) {
            assertTrue(diverse[i].relevanceScore >= diverse[i + 1].relevanceScore)
        }
    }
}
