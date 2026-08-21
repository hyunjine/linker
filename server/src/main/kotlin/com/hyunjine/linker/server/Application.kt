package com.hyunjine.linker.server

import com.hyunjine.linker.server.auth.authRoutes
import com.hyunjine.linker.server.common.installCommonPlugins
import com.hyunjine.linker.server.db.Database
import com.hyunjine.linker.server.users.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.response.respond
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ktor Netty 진입점. `application.yaml` 의 `ktor.application.modules` 가 아래 [module] 을 가리킨다.
 * 로컬: `./gradlew :server:run` — 8080 포트.
 * 배포: `java -jar linker-server.jar` (Cloud Run 컨테이너). $PORT 환경변수를 EngineMain 이 읽음.
 */
fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** 전 모듈에서 공유되는 JSON 직렬화 설정. lenient 하되 unknown key 는 무시. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

fun Application.module() {
    log.info("Starting Linker server ...")

    // DB 커넥션 풀 초기화 + Flyway 마이그레이션. DB 가 없으면 헬스체크가 실패해 배포가 롤백됨.
    Database.init(environment.config)

    installCommonPlugins()

    install(ContentNegotiation) {
        json(AppJson)
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", db = if (Database.isHealthy()) "ok" else "down"))
        }
        authRoutes()
        userRoutes()
    }
}

@Serializable
data class HealthResponse(val status: String, val db: String)
