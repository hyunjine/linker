package com.hyunjine.linker.server.common

import com.hyunjine.linker.api.common.ApiError
import com.hyunjine.linker.api.common.ErrorBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.event.Level

/**
 * 도메인 모듈이 아닌, 서버 전역에서 공통으로 켜는 Ktor 플러그인 묶음.
 * Application.kt 가 얇게 유지되도록 여기에 집약.
 */
fun Application.installCommonPlugins() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(CORS) {
        // v0: 모바일 앱 전용이라 origin 제약이 사실상 없음. 관리 콘솔이 붙으면 origin allowlist 로.
        anyHost()
        allowHeader("Authorization")
        allowHeader("Content-Type")
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Patch)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorBody(ApiError(cause.code, cause.message ?: cause.code)))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorBody(ApiError("INTERNAL", "internal server error")),
            )
        }
    }
}

/**
 * 도메인 코드에서 던지는 표준 API 예외. StatusPages 가 잡아 [ErrorBody] (shared-api) 로 직렬화.
 * `code` 는 `docs/api-design.md` §1.4 에 정의된 문자열 상수.
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String,
) : RuntimeException(message)
