package com.insuranceai.policy.controller

import com.insuranceai.policy.domain.InsuranceType
import com.insuranceai.policy.entity.InsuranceCompanyEntity
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.service.PolicyService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyControllerTest {
    private val policyService = mockk<PolicyService>()
    private val insuranceCompanyRepository = mockk<InsuranceCompanyRepository>()
    private val controller = PolicyController(policyService, insuranceCompanyRepository)

    private fun createCompanyEntity(
        name: String,
        type: InsuranceType,
    ) = InsuranceCompanyEntity(
        id = UUID.randomUUID(),
        name = name,
        type = type.name,
        createdAt = Instant.now(),
    )

    @Test
    fun `보험사 목록 조회 성공`() =
        runTest {
            val entities =
                flowOf(
                    createCompanyEntity("삼성생명", InsuranceType.LIFE),
                    createCompanyEntity("현대해상", InsuranceType.NON_LIFE),
                )
            coEvery { insuranceCompanyRepository.findByIsActiveTrue() } returns entities

            val result = controller.getInsuranceCompanies()

            assertTrue(result.success)
            assertEquals(2, result.data!!.size)
            assertEquals("삼성생명", result.data!![0].name)
            assertEquals(InsuranceType.LIFE, result.data!![0].type)
            assertEquals("현대해상", result.data!![1].name)
            assertEquals(InsuranceType.NON_LIFE, result.data!![1].type)
        }

    @Test
    fun `카테고리 전체 목록 조회`() =
        runTest {
            val result = controller.getCategories(null)

            assertTrue(result.success)
            assertEquals(10, result.data!!.size)
        }

    @Test
    fun `생보 카테고리만 필터링`() =
        runTest {
            val result = controller.getCategories(InsuranceType.LIFE)

            assertTrue(result.success)
            assertEquals(6, result.data!!.size)
            assertTrue(result.data!!.all { it.insuranceType == InsuranceType.LIFE })
        }

    @Test
    fun `손보 카테고리만 필터링`() =
        runTest {
            val result = controller.getCategories(InsuranceType.NON_LIFE)

            assertTrue(result.success)
            assertEquals(4, result.data!!.size)
            assertTrue(result.data!!.all { it.insuranceType == InsuranceType.NON_LIFE })
        }
}
