import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// :adminWeb — Compose Multiplatform · Kotlin/Wasm 브라우저 타깃.
// docs/261-admin-push-console.md §5 — 관리자 푸시 발송 콘솔의 Gradle 부트스트랩.
// #278 부터 인증/세션/화이트리스트 로직을 이 모듈 안에서 자립적으로 구현한다.
// `:shared` 의 expect/actual 이 iOS/Android 만을 대상으로 하여 wasmJs 로 컴파일되지 못하므로
// 의존을 걸지 않는다 (설계 결정 — docs §5, #278 코멘트 참조).

// 시크릿 소스 규칙 (:shared 와 동일 패턴):
//   1) `local.properties` — gitignore. 로컬 개발 override.
//   2) 환경 변수 — CI (`ADMIN_WEB_SUPABASE_URL` 등) 에서 주입. #276 gh-pages 워크플로가 이 이름을
//      그대로 export 하도록 맞춰둔다.
// 두 소스 모두 비어 있으면 빈 문자열로 상수를 발행 → 개발자가 인지하도록 초기화 시 예외.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * [localKey] (`local.properties`) → [envKey] (환경 변수) 순으로 시크릿을 조회한다.
 * 둘 다 비어 있으면 빈 문자열. 빌드 실패는 유발하지 않는다 (배포 파이프라인이 채워야 함).
 */
fun secret(localKey: String, envKey: String): String =
    localProperties.getProperty(localKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey).orEmpty()

val supabaseUrl: String = secret("adminweb.supabase.url", "ADMIN_WEB_SUPABASE_URL")
val supabaseAnonKey: String = secret("adminweb.supabase.anonKey", "ADMIN_WEB_SUPABASE_ANON_KEY")
// 콤마 구분 UID 리스트 — `uid1,uid2,uid3`. 화이트리스트가 비어 있으면 관리자 없음으로 간주.
val adminUids: String = secret("adminweb.admin.uids", "ADMIN_WEB_ADMIN_UIDS")

val generatedSecretsDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/secrets/kotlin")

val generateAdminSecrets by tasks.registering {
    val outputDir = generatedSecretsDir
    val sbUrl = supabaseUrl
    val sbKey = supabaseAnonKey
    val uids = adminUids
    inputs.property("supabaseUrl", sbUrl)
    inputs.property("supabaseAnonKey", sbKey)
    inputs.property("adminUids", uids)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("com/hyunjine/linker/adminweb/config/AdminWebSecrets.kt")
        file.parentFile.mkdirs()
        // 콤마 구분 문자열을 그대로 상수로 넣고 런타임에 split. 배포 이후 uid 변경 시에도
        // 재빌드가 필요하지만, 관리자 수가 적어 실용적으로 무리 없음.
        file.writeText(
            """
            // GENERATED. Do not edit. Source: local.properties → adminWeb/build.gradle.kts
            package com.hyunjine.linker.adminweb.config

            internal object AdminWebSecrets {
                /** Supabase Project URL. `https://<ref>.supabase.co`. 빈 문자열이면 초기화 실패. */
                const val SupabaseUrl: String = "$sbUrl"

                /** Supabase anon (publishable) key. 브라우저 baked-in. RLS 로 접근 통제. */
                const val SupabaseAnonKey: String = "$sbKey"

                /**
                 * 관리자 화이트리스트. 콤마 구분 UID 문자열 (예: `uid1,uid2,uid3`).
                 * 비어 있으면 어떤 로그인도 관리자 자격을 얻지 못한다 → 콘솔 진입 불가.
                 * 실제 uid 는 배포 시점에 `ADMIN_WEB_ADMIN_UIDS` 환경 변수로 주입.
                 */
                const val AdminUidsRaw: String = "$uids"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    // shared / androidApp 과 동일. Foojay 리졸버가 JDK 21 을 자동 조달.
    jvmToolchain(21)

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("adminWeb")
        browser {
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "adminWeb.js"
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    // dev 서버가 index.html 을 서빙할 정적 리소스 루트.
                    static(projectDirPath + "/src/wasmJsMain/resources")
                }
            }
        }
        binaries.executable()
    }

    sourceSets.named("wasmJsMain") {
        // 시크릿 생성 태스크의 출력 경로를 wasmJsMain 소스 세트에 등록 →
        // 컴파일 시점에 자동으로 AdminWebSecrets.kt 가 함께 컴파일된다.
        kotlin.srcDir(generateAdminSecrets.map { generatedSecretsDir })

        dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // supabase-kt (wasmJs) — Auth 는 세션 + email/password 로그인, Functions 는
            // send-announcement 위임 (#279 이후) 을 위해 미리 설치해둔다.
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.functions)
            // 실제 HTTP 는 Ktor CIO 엔진이 담당. wasmJs 는 CIO 외에 엔진 옵션이 없다.
            implementation(libs.ktor.client.cio)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.hyunjine.linker.adminweb.resources"
    generateResClass = auto
}
