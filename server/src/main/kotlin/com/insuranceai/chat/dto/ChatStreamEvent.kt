package com.insuranceai.chat.dto

import com.insuranceai.policy.dto.PremiumExampleItem
import com.insuranceai.policy.dto.ProductFeatureItem
import com.insuranceai.policy.dto.ProductIndexItem
import com.insuranceai.policy.dto.ProductMetricItem
import com.insuranceai.policy.dto.SurrenderValueItem
import com.insuranceai.policy.dto.YieldScenarioItem
import java.util.UUID

/** SSE 이벤트 래퍼 (02-search-chat.md 기반) */
data class ChatStreamEvent(
    val type: String,
    val data: Any,
)

data class SearchingEvent(
    val message: String,
)

data class SourcesFoundEvent(
    val sources: List<SourceInfo>,
)

data class GeneratingEvent(
    val message: String,
)

data class TokenEvent(
    val content: String,
)

data class DoneEvent(
    val messageId: UUID,
    val suggestedQuestions: List<String>,
    val supplementary: SupplementaryData? = null,
    val title: String? = null,
)

data class SupplementaryData(
    val surrenderValues: List<SurrenderValueItem> = emptyList(),
    val premiumExamples: List<PremiumExampleItem> = emptyList(),
    val priceIndices: List<ProductIndexItem> = emptyList(),
    val productFeatures: List<ProductFeatureItem> = emptyList(),
    val yieldScenarios: List<YieldScenarioItem> = emptyList(),
    val productMetrics: List<ProductMetricItem> = emptyList(),
)

data class ErrorEvent(
    val code: String,
    val message: String,
)

data class SourceInfo(
    val articleId: UUID,
    val insuranceCompany: String,
    val productId: UUID,
    val productName: String,
    val contentType: String,
    val articleNumber: String?,
    val articleTitle: String?,
    val relevanceScore: Float,
    val dataSource: String,
    val dataSourceId: UUID? = null,
)
