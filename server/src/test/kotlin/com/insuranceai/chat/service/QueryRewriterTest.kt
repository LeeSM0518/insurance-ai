package com.insuranceai.chat.service

import com.insuranceai.chat.entity.ChatMessageEntity
import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryRewriterTest {
    private val suspendingChatClient = mockk<SuspendingChatClient>()
    private val promptLoader =
        mockk<PromptLoader> {
            every { load("chat/query-rewrite.st") } returns "재작성 프롬프트"
        }

    private val rewriter = QueryRewriter(suspendingChatClient, promptLoader)

    private val sessionId = UUID.randomUUID()

    private fun createMessage(
        role: String,
        content: String,
    ) = ChatMessageEntity(
        id = UUID.randomUUID(),
        chatSessionId = sessionId,
        role = role,
        content = content,
    )

    @Test
    fun `대화 이력이 없으면 원본 질문을 그대로 반환`() =
        runTest {
            val result = rewriter.rewrite("면책사항은?", emptyList())

            assertEquals("면책사항은?", result)
            // LLM 호출 없음
            coVerify(exactly = 0) { suspendingChatClient.call(any(), any()) }
        }

    @Test
    fun `대화 이력이 있으면 LLM을 호출하여 재작성`() =
        runTest {
            val history =
                listOf(
                    createMessage("USER", "삼성생명 암보험 보장 알려줘"),
                    createMessage("ASSISTANT", "삼성생명 암보험의 보장 내용은..."),
                )

            coEvery {
                suspendingChatClient.call(any(), any())
            } returns "삼성생명 암보험의 면책사항"

            val result = rewriter.rewrite("면책사항은?", history)

            assertEquals("삼성생명 암보험의 면책사항", result)
        }

    @Test
    fun `LLM 호출 실패 시 원본 질문 반환`() =
        runTest {
            val history = listOf(createMessage("USER", "이전 질문"))

            coEvery {
                suspendingChatClient.call(any(), any())
            } throws RuntimeException("API 에러")

            val result = rewriter.rewrite("면책사항은?", history)

            assertEquals("면책사항은?", result)
        }

    @Test
    fun `LLM이 빈 문자열 반환 시 원본 질문 반환`() =
        runTest {
            val history = listOf(createMessage("USER", "이전 질문"))

            coEvery {
                suspendingChatClient.call(any(), any())
            } returns ""

            val result = rewriter.rewrite("면책사항은?", history)

            assertEquals("면책사항은?", result)
        }

    @Test
    fun `최근 6개 메시지만 사용한다`() =
        runTest {
            val history =
                (1..10).map { i ->
                    createMessage(if (i % 2 == 1) "USER" else "ASSISTANT", "메시지 $i")
                }

            coEvery {
                suspendingChatClient.call(match { it.contains("메시지 5") && !it.contains("메시지 4") }, any())
            } returns "재작성된 질문"

            val result = rewriter.rewrite("현재 질문", history)

            assertEquals("재작성된 질문", result)
        }
}
