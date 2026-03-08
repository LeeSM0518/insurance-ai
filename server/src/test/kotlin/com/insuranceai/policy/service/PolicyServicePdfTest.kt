package com.insuranceai.policy.service

import com.insuranceai.policy.entity.DataSourceEntity
import com.insuranceai.policy.error.PolicyError
import com.insuranceai.policy.repository.DataSourceRepository
import com.insuranceai.policy.repository.InsuranceCompanyRepository
import com.insuranceai.policy.repository.PolicyArticleRepository
import com.insuranceai.policy.repository.PremiumExampleRepository
import com.insuranceai.policy.repository.ProductFeatureRepository
import com.insuranceai.policy.repository.ProductIndexRepository
import com.insuranceai.policy.repository.ProductMetricRepository
import com.insuranceai.policy.repository.ProductRepository
import com.insuranceai.policy.repository.ProductYieldScenarioRepository
import com.insuranceai.policy.repository.SurrenderValueRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PolicyServicePdfTest {
    private val dataSourceRepository = mockk<DataSourceRepository>()
    private val policyService =
        PolicyService(
            policyArticleRepository = mockk<PolicyArticleRepository>(),
            productRepository = mockk<ProductRepository>(),
            insuranceCompanyRepository = mockk<InsuranceCompanyRepository>(),
            dataSourceRepository = dataSourceRepository,
            surrenderValueRepository = mockk<SurrenderValueRepository>(),
            premiumExampleRepository = mockk<PremiumExampleRepository>(),
            productIndexRepository = mockk<ProductIndexRepository>(),
            productFeatureRepository = mockk<ProductFeatureRepository>(),
            productYieldScenarioRepository = mockk<ProductYieldScenarioRepository>(),
            productMetricRepository = mockk<ProductMetricRepository>(),
        )

    @Test
    fun `존재하지 않는 dataSourceId로 조회 시 DataSourceNotFound 발생`() =
        runTest {
            val id = UUID.randomUUID()
            coEvery { dataSourceRepository.findById(id) } returns null

            assertFailsWith<PolicyError.DataSourceNotFound> {
                policyService.getPdfResource(id)
            }
        }

    @Test
    fun `filePath가 null인 DataSource 조회 시 PdfFileNotFound 발생`() =
        runTest {
            val id = UUID.randomUUID()
            coEvery { dataSourceRepository.findById(id) } returns
                DataSourceEntity(
                    id = id,
                    sourceType = "ASSOCIATION",
                    filePath = null,
                )

            assertFailsWith<PolicyError.PdfFileNotFound> {
                policyService.getPdfResource(id)
            }
        }

    @Test
    fun `Path Traversal 공격 경로 차단`() =
        runTest {
            val id = UUID.randomUUID()
            coEvery { dataSourceRepository.findById(id) } returns
                DataSourceEntity(
                    id = id,
                    sourceType = "ASSOCIATION",
                    filePath = "data/../../../etc/passwd",
                )

            assertFailsWith<PolicyError.InvalidFilePath> {
                policyService.getPdfResource(id)
            }
        }

    @Test
    fun `파일이 존재하지 않는 경로 조회 시 PdfFileNotFound 발생`() =
        runTest {
            val id = UUID.randomUUID()
            coEvery { dataSourceRepository.findById(id) } returns
                DataSourceEntity(
                    id = id,
                    sourceType = "ASSOCIATION",
                    filePath = "data/nonexistent/file.pdf",
                )

            assertFailsWith<PolicyError.PdfFileNotFound> {
                policyService.getPdfResource(id)
            }
        }

    @Test
    fun `유효한 PDF 파일 경로로 조회 시 Resource 반환`() =
        runTest {
            // data 디렉토리에 임시 PDF 파일 생성
            val dataDir =
                Files.createDirectories(
                    java.nio.file.Path
                        .of("data", "test"),
                )
            val tempFile = Files.createTempFile(dataDir, "test", ".pdf")
            try {
                Files.write(tempFile, "%PDF-1.4 test".toByteArray())

                val id = UUID.randomUUID()
                coEvery { dataSourceRepository.findById(id) } returns
                    DataSourceEntity(
                        id = id,
                        sourceType = "PRODUCT_SUMMARY",
                        filePath = tempFile.toString(),
                    )

                val resource = policyService.getPdfResource(id)
                assertNotNull(resource)
            } finally {
                Files.deleteIfExists(tempFile)
                Files.deleteIfExists(dataDir)
            }
        }
}
