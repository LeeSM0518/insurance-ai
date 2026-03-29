package com.insuranceai.report.error

import com.insuranceai.common.error.ServiceError
import org.springframework.http.HttpStatus

sealed class ReportError(
    override val message: String,
) : ServiceError() {
    override val mainCode = "REPORT"

    data object NotFound : ReportError("리포트를 찾을 수 없습니다") {
        override val status = HttpStatus.NOT_FOUND
    }

    data object NotOwner : ReportError("접근 권한이 없는 리포트입니다") {
        override val status = HttpStatus.FORBIDDEN
    }

    data object ShareLinkExpired : ReportError("공유 링크가 만료되었습니다") {
        override val status = HttpStatus.NOT_FOUND
    }

    data object InvalidCompanyCount : ReportError("비교 보험사는 2~5개를 선택해야 합니다") {
        override val status = HttpStatus.BAD_REQUEST
    }

    data object CompanyNotFound : ReportError("존재하지 않는 보험사가 포함되어 있습니다") {
        override val status = HttpStatus.BAD_REQUEST
    }
}
