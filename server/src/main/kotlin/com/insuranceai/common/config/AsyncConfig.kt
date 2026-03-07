package com.insuranceai.common.config

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 앱 생명주기와 동일한 CoroutineScope 제공
 * HTTP 응답 후 비동기 후처리(채팅 메시지 저장, 피드백 기록, 캐시 무효화)에 사용
 */
@Configuration
class AsyncConfig {
    @Bean
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("app"))
}
