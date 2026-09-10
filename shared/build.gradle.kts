import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// 앱 시크릿은 두 소스에서 읽는다:
//   1) `iosApp/Configuration/Config.xcconfig` — 저장소에 checkin. Xcode Cloud/CI 빌드도
//      항상 값을 갖도록 하기 위함. 여기 있는 값은 결국 IPA/APK 에 baked in 되어 배포되므로
//      "숨겨봤자 얻는 게 없는" 성격 (Supabase publishable key · OAuth 공개 식별자 등).
//   2) `local.properties` — gitignore. 로컬 개발용 override. 같은 키가 있으면 우선.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val xcconfigProperties: Map<String, String> = rootProject.file("iosApp/Configuration/Config.xcconfig")
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("//")) return@mapNotNull null
        val eq = line.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        // Xcode 는 `//` 뒤를 주석으로 취급하지만, 우리는 build 시점에만 값을 읽어 Kotlin
        // 상수로 baked-in 하므로 URL 등을 그대로 사용해도 무방 (Xcode build setting 으로
        // 는 노출되지 않는다).
        line.substring(0, eq).trim() to line.substring(eq + 1).trim()
    }
    ?.toMap()
    .orEmpty()

/** local.properties (dev override) 우선 → 없으면 Config.xcconfig 값. 둘 다 없으면 "". */
fun secret(localKey: String, xcconfigKey: String): String =
    localProperties.getProperty(localKey)?.takeIf { it.isNotBlank() }
        ?: xcconfigProperties[xcconfigKey].orEmpty()

val holidayApiKey: String = secret("holiday.api.key", "HOLIDAY_API_KEY")
val supabaseUrl: String = secret("supabase.url", "SUPABASE_URL")
val supabasePublishableKey: String = secret("supabase.publishableKey", "SUPABASE_PUBLISHABLE_KEY")
val googleWebClientId: String = secret("google.web.client.id", "GOOGLE_WEB_CLIENT_ID")
val outlookClientId: String = secret("outlook.client.id", "OUTLOOK_CLIENT_ID")

val generatedSecretsDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/secrets/kotlin")

val generateSecrets by tasks.registering {
    val outputDir = generatedSecretsDir
    val holidayKey = holidayApiKey
    val sbUrl = supabaseUrl
    val sbKey = supabasePublishableKey
    val googleWeb = googleWebClientId
    val outlookClient = outlookClientId
    inputs.property("holidayApiKey", holidayKey)
    inputs.property("supabaseUrl", sbUrl)
    inputs.property("supabasePublishableKey", sbKey)
    inputs.property("googleWebClientId", googleWeb)
    inputs.property("outlookClientId", outlookClient)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("com/hyunjine/linker/data/Secrets.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            // GENERATED. Do not edit. Source: local.properties → shared/build.gradle.kts
            package com.hyunjine.linker.data

            internal object Secrets {
                /** data.go.kr 특일정보 API 서비스 키 (URL-encoded 원본). local.properties `holiday.api.key`. */
                const val HolidayApiKey: String = "$holidayKey"

                /** Supabase Project URL. `https://<ref>.supabase.co`. local.properties `supabase.url`. */
                const val SupabaseUrl: String = "$sbUrl"

                /** Supabase Publishable key (`sb_publishable_...`). 클라이언트에 안전하게 임베드. local.properties `supabase.publishableKey`. */
                const val SupabasePublishableKey: String = "$sbKey"

                /**
                 * Google **Web** OAuth 2.0 Client ID (형식: `NUMBER-HASH.apps.googleusercontent.com`).
                 * Android Credential Manager 의 GetGoogleIdOption 에 `serverClientId` 로 넘겨야 하고,
                 * Supabase 는 이 값 (또는 iOS Client ID) 을 authorized audience 로 검증한다.
                 * local.properties `google.web.client.id`. Google Cloud Console → APIs & Services →
                 * Credentials 에서 Web application 타입으로 생성한 것.
                 */
                const val GoogleWebClientId: String = "$googleWeb"

                /**
                 * Microsoft Entra ID (Azure AD) 앱 등록의 Application (client) ID (GUID).
                 * Multi-tenant + personal accounts 로 등록해서 MSAL SDK 에 `common` authority 와 함께
                 * 넘긴다. Graph API 접근 audience 로 소비. local.properties `outlook.client.id`.
                 */
                const val OutlookClientId: String = "$outlookClient"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    // Foojay resolver (settings.gradle.kts) 가 로컬에 없으면 JDK 21 을 자동 다운로드.
    // Xcode Cloud · CI · 새 팀원 온보딩 모두 zero-config. jvmTarget 은 여전히 JVM_11
    // 유지 (Android 최소 지원 · 컴파일된 바이트코드는 backward-compatible).
    jvmToolchain(21)

    // `expect`/`actual` classes 는 Beta 상태 (KT-61573) — 프로젝트가 이미 다수의 expect 를
    // 사용 중이라 경고를 통째로 억제. 정식 stabilize 되면 이 플래그 제거.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
       namespace = "com.hyunjine.linker.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            // Google Sign-In via Credential Manager (modern API)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            // Microsoft MSAL — Outlook (Microsoft Entra ID) 로그인 → Graph API access_token.
            // MSAL 은 Surface Duo 전용 `display-mask` 를 transitive 로 물고 있는데
            // Maven Central 에 없고 Microsoft Duo SDK 전용 repo 에만 있음. 그래서
            // settings.gradle.kts 에 Microsoft Duo repo 추가 (com.microsoft.device 그룹 한정).
            implementation(libs.msal)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation3.ui)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        commonMain {
            kotlin.srcDir(generateSecrets.map { generatedSecretsDir })
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
