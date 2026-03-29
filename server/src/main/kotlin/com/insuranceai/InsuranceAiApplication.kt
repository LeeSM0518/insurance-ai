package com.insuranceai

import io.github.cdimascio.dotenv.Dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    exclude = [org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration::class],
)
@ConfigurationPropertiesScan
@EnableScheduling
class InsuranceAiApplication

fun main(args: Array<String>) {
    val dotenv =
        Dotenv
            .configure()
            .ignoreIfMissing()
            .load()
    dotenv.entries().forEach { entry ->
        System.setProperty(entry.key, entry.value)
    }
    runApplication<InsuranceAiApplication>(*args)
}
