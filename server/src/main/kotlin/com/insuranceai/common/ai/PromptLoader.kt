package com.insuranceai.common.ai

import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.template.st.StTemplateRenderer
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 프롬프트 파일 로드 및 변수 치환을 담당하는 컴포넌트.
 * classpath:prompts/ 하위의 .st 파일을 로드하고, StringTemplate 기반으로 변수를 치환합니다.
 */
@Component
class PromptLoader {
    private val renderer =
        StTemplateRenderer
            .builder()
            .startDelimiterToken('<')
            .endDelimiterToken('>')
            .build()

    private val resourceCache = ConcurrentHashMap<String, Resource>()

    /** 프롬프트 파일을 읽어 문자열로 반환 */
    fun load(path: String): String = getResource(path).getContentAsString(Charsets.UTF_8)

    /** 프롬프트 파일을 읽고 변수를 치환하여 반환 */
    fun render(
        path: String,
        variables: Map<String, Any>,
    ): String =
        PromptTemplate
            .builder()
            .renderer(renderer)
            .resource(getResource(path))
            .build()
            .render(variables)

    private fun getResource(path: String): Resource =
        resourceCache.getOrPut(path) {
            ClassPathResource("prompts/$path")
        }
}
