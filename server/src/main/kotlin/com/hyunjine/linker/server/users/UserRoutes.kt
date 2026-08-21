package com.hyunjine.linker.server.users

import com.hyunjine.linker.api.common.ApiError
import com.hyunjine.linker.api.common.ErrorBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * `docs/api-design.md` §4 Users. 스캐폴딩 단계에서는 GET /users/me 스텁만.
 * POST /users/me/profile · PATCH /users/me · preferences · uploads 는 후속 이슈.
 */
fun Route.userRoutes() {
    route("/users/me") {
        get {
            // TODO(#후속): JWT auth → DB users 조회 → UserResponse 직렬화
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorBody(ApiError("NOT_IMPLEMENTED", "GET /users/me not implemented yet")),
            )
        }
    }
}
