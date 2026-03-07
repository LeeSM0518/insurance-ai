package com.insuranceai.pipeline.parser

/**
 * 파싱 과정에서 필수 데이터 추출 실패 시 발생하는 예외.
 * 추출 실패 로그를 통해 정규식 패턴을 지속 보강.
 */
class ParsingException(
    message: String,
) : RuntimeException(message)
