package com.insuranceai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication(
    exclude = [org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration::class],
)
@ConfigurationPropertiesScan
class InsuranceAiApplication

fun main(args: Array<String>) {
    runApplication<InsuranceAiApplication>(*args)
}
