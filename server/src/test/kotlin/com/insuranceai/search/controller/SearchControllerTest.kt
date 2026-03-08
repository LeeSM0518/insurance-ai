package com.insuranceai.search.controller

import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.search.dto.SearchRequest
import com.insuranceai.search.dto.SearchResponse
import com.insuranceai.search.error.SearchError
import com.insuranceai.search.service.SearchService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SearchControllerTest {
    private val searchService = mockk<SearchService>()
    private val controller = SearchController(searchService)
    private val principal = UserPrincipal(userId = UUID.randomUUID(), nickname = "test")

    private val mockSearchResponse =
        SearchResponse(
            query = "암보험",
            totalCount = 1,
            page = 0,
            size = 20,
            hasNext = false,
            results = emptyList(),
        )

    @Test
    fun `검색 성공 시 200과 SearchResponse 반환`() =
        runTest {
            coEvery { searchService.search(any(), any(), any()) } returns mockSearchResponse

            val request = SearchRequest(query = "암보험")
            val result = controller.search(request, principal)

            assertEquals(HttpStatus.OK, result.statusCode)
            assertTrue(result.body!!.success)
            assertEquals("암보험", result.body!!.data!!.query)
        }

    @Test
    fun `빈 쿼리 시 SearchError QueryMissing 발생`() =
        runTest {
            coEvery { searchService.search(any(), any(), any()) } throws SearchError.QueryMissing

            val request = SearchRequest(query = "")
            assertFailsWith<SearchError.QueryMissing> {
                controller.search(request, principal)
            }
        }

    @Test
    fun `긴 쿼리 시 SearchError QueryTooLong 발생`() =
        runTest {
            coEvery { searchService.search(any(), any(), any()) } throws SearchError.QueryTooLong

            val request = SearchRequest(query = "a".repeat(201))
            assertFailsWith<SearchError.QueryTooLong> {
                controller.search(request, principal)
            }
        }
}
