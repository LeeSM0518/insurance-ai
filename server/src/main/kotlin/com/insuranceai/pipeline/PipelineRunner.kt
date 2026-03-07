package com.insuranceai.pipeline

import com.insuranceai.pipeline.usecase.RunPipelineUseCase
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("pipeline")
class PipelineRunner(
    private val runPipelineUseCase: RunPipelineUseCase,
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runBlocking {
            logger.info("파이프라인 CLI 모드 시작")

            val result = runPipelineUseCase.runFullPipeline()

            logger.info(
                """
                ===== 파이프라인 실행 리포트 =====
                소요 시간: ${result.totalDuration}
                생보: 수집=${result.life.crawled}, 파싱=${result.life.parsed}, 검증=${result.life.validated}, 반영=${result.life.published}, 임베딩=${result.life.embedded}
                손보: 수집=${result.nonLife.crawled}, 파싱=${result.nonLife.parsed}, 검증=${result.nonLife.validated}, 반영=${result.nonLife.published}, 임베딩=${result.nonLife.embedded}
                에러: ${(result.life.errors + result.nonLife.errors).joinToString("; ")}
                ================================
                """.trimIndent(),
            )
        }
    }
}
