import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.hyunjine.linker"
version = "0.0.1"

application {
    mainClass = "com.hyunjine.linker.server.ApplicationKt"
    // 로컬 개발 편의: -Pio.ktor.development=true 로 실행 시 auto-reload
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=${findProperty("io.ktor.development") ?: "false"}",
    )
}

// 로컬 개발용 DB 접속 정보를 local.properties → 실행 시 env 로 자동 주입.
// application.yaml 이 `$DB_URL:default` 형태로 env 를 읽는다. 배포 (Fly.io) 에서는
// `fly secrets set DB_URL=...` 로 별도 주입되므로 이 블록은 로컬 실행에만 영향.
private val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
tasks.named<JavaExec>("run") {
    listOf("db.url" to "DB_URL", "db.user" to "DB_USER", "db.password" to "DB_PASSWORD").forEach { (k, envKey) ->
        localProps.getProperty(k)?.takeIf { it.isNotBlank() }?.let { environment(envKey, it) }
    }
}

ktor {
    // 배포용 fat jar 이름 고정. Dockerfile 에서 이 경로를 참조.
    fatJar {
        archiveFileName.set("linker-server.jar")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared-api"))

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.config.yaml)

    // 카카오 API 검증 등 서버측 HTTP 호출
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // 도메인/DTO
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    // DB
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Logging
    implementation(libs.logback.classic)

    // Test
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}
