package com.insuranceai.policy.controller

import com.insuranceai.common.security.UserPrincipal
import com.insuranceai.policy.dto.PremiumExampleResponse
import com.insuranceai.policy.dto.ProductIndexResponse
import com.insuranceai.policy.dto.SurrenderValueResponse
import com.insuranceai.policy.service.PolicyService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductControllerTest {
    private val policyService = mockk<PolicyService>()
    private val controller = ProductController(policyService)
    private val principal = UserPrincipal(userId = UUID.randomUUID(), nickname = "test")
    private val productId = UUID.randomUUID()

    @Test
    fun `해약환급금 조회 성공 시 200과 SurrenderValueResponse 반환`() =
        runTest {
            val mockResponse =
                SurrenderValueResponse(
                    productId = productId,
                    values = emptyList(),
                )
            coEvery { policyService.getSurrenderValues(productId) } returns mockResponse

            val result = controller.getSurrenderValues(productId, principal)

            assertTrue(result.success)
            assertEquals(productId, result.data!!.productId)
        }

    @Test
    fun `보험료 예시 조회 성공 시 200과 PremiumExampleResponse 반환`() =
        runTest {
            val mockResponse =
                PremiumExampleResponse(
                    productId = productId,
                    examples = emptyList(),
                )
            coEvery { policyService.getPremiumExamples(productId) } returns mockResponse

            val result = controller.getPremiumExamples(productId, principal)

            assertTrue(result.success)
            assertEquals(productId, result.data!!.productId)
        }

    @Test
    fun `가격지수 조회 성공 시 200과 ProductIndexResponse 반환`() =
        runTest {
            val mockResponse =
                ProductIndexResponse(
                    productId = productId,
                    indices = emptyList(),
                )
            coEvery { policyService.getProductIndices(productId) } returns mockResponse

            val result = controller.getProductIndices(productId, principal)

            assertTrue(result.success)
            assertEquals(productId, result.data!!.productId)
        }

    @Test
    fun `존재하지 않는 상품 조회 시 빈 리스트 반환`() =
        runTest {
            val emptyProductId = UUID.randomUUID()
            val mockResponse =
                SurrenderValueResponse(
                    productId = emptyProductId,
                    values = emptyList(),
                )
            coEvery { policyService.getSurrenderValues(emptyProductId) } returns mockResponse

            val result = controller.getSurrenderValues(emptyProductId, principal)

            assertTrue(result.success)
            assertTrue(result.data!!.values.isEmpty())
        }
}
