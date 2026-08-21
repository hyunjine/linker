package com.hyunjine.linker.server.auth

import com.hyunjine.linker.api.auth.KakaoLoginRequest
import com.hyunjine.linker.api.common.ApiError
import com.hyunjine.linker.api.common.ErrorBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `docs/api-design.md` §3 Auth. 이번 이슈 스코프는 스텁 (스캐폴딩) 만.
 * 카카오 실제 검증 · 서비스 세션 발급 · refresh 는 후속 이슈 (#17) 에서 완성한다.
 */
fun Route.authRoutes() {
    route("/auth") {
        post("/kakao") {
            val req = call.receive<KakaoLoginRequest>()
            // TODO(#17): 카카오 access_token 검증 → 유저 shell upsert → JWT 발급
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorBody(ApiError(
                    code = "NOT_IMPLEMENTED",
                    message = "kakao login not implemented yet; token prefix=${req.kakaoAccessToken.take(6)}",
                )),
            )
        }
    }
}
