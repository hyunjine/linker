package com.hyunjine.linker.api.common

import kotlinx.serialization.Serializable

/**
 * `docs/api-design.md` §1.4 표준 에러 응답. 서버 [ErrorBody] 와 클라 에러 파싱 모두 이 DTO 사용.
 */
@Serializable
data class ErrorBody(val error: ApiError)

@Serializable
data class ApiError(
    /** 문자열 상수. 예: `VALIDATION_FAILED`, `UNAUTHENTICATED`, `ALREADY_COMPLETED`. */
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
