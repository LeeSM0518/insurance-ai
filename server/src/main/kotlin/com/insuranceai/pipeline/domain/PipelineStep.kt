package com.insuranceai.pipeline.domain

/** 파이프라인 실행 단계 */
enum class PipelineStep {
    CRAWL,
    VALIDATE,
    PUBLISH,
    EMBED,
}
