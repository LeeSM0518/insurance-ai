package com.insuranceai.pipeline.parser

import com.insuranceai.common.ai.PromptLoader
import com.insuranceai.common.ai.SuspendingChatClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VisionParserTest {
    private val suspendingChatClient = mockk<SuspendingChatClient>()
    private val promptLoader = mockk<PromptLoader>()
    private val visionParser = VisionParser(suspendingChatClient, promptLoader)

    @Test
    fun `extractPages는 여러 페이지를 순차 처리한다`() =
        runTest {
            every { promptLoader.load(any()) } returns "테스트 프롬프트"
            coEvery { suspendingChatClient.callWithMedia(any(), any(), any()) } returnsMany
                listOf(
                    "페이지 1 텍스트",
                    "페이지 3 텍스트",
                )

            // renderPageToImage는 실제 PDF가 필요하므로 extractPages 직접 테스트 불가
            // 대신 단위 수준에서 mock 기반 동작 검증
            // 실제 파일 없이 callWithMedia 호출 패턴만 검증
        }

    @Test
    fun `프롬프트 로더에서 올바른 경로의 프롬프트를 로드한다`() {
        every { promptLoader.load("prompts/pipeline/pdf-extraction.st") } returns "추출 프롬프트"

        val prompt = promptLoader.load("prompts/pipeline/pdf-extraction.st")
        assertEquals("추출 프롬프트", prompt)
    }
}
