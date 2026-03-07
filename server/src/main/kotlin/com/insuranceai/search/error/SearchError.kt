package com.insuranceai.search.error

import com.insuranceai.common.error.ServiceError
import org.springframework.http.HttpStatus

sealed class SearchError(
    override val message: String,
) : ServiceError() {
    override val mainCode = "SEARCH"

    data object QueryMissing : SearchError("검색어를 입력해주세요") {
        override val status = HttpStatus.BAD_REQUEST
    }

    data object QueryTooLong : SearchError("검색어는 200자를 초과할 수 없습니다") {
        override val status = HttpStatus.BAD_REQUEST
    }
}
