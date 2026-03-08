package com.insuranceai.policy.error

import com.insuranceai.common.error.ServiceError
import org.springframework.http.HttpStatus

sealed class PolicyError(
    override val message: String,
) : ServiceError() {
    override val mainCode = "POLICY"

    data object ArticleNotFound : PolicyError("약관 조문을 찾을 수 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }

    data object DataSourceNotFound : PolicyError("데이터 소스를 찾을 수 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }

    data object PdfFileNotFound : PolicyError("PDF 파일을 찾을 수 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }

    data object InvalidFilePath : PolicyError("유효하지 않은 파일 경로입니다") {
        override val status = HttpStatus.BAD_REQUEST
    }
}
