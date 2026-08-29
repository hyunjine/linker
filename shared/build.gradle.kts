import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// local.properties 의 holiday.api.key 를 읽어 commonMain 에 생성되는 Secrets.kt 파일에 넣는다.
// local.properties 는 gitignore 되어 있어 키가 저장소에 안 들어감.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val holidayApiKey: String = localProperties.getProperty("holiday.api.key", "")
val supabaseUrl: String = localProperties.getProperty("supabase.url", "")
val supabasePublishableKey: String = localProperties.getProperty("supabase.publishableKey", "")
val kakaoNativeAppKey: String = localProperties.getProperty("kakao.native.app.key", "")

val generatedSecretsDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/secrets/kotlin")

val generateSecrets by tasks.registering {
    val outputDir = generatedSecretsDir
    val holidayKey = holidayApiKey
    val sbUrl = supabaseUrl
    val sbKey = supabasePublishableKey
    val kakaoKey = kakaoNativeAppKey
    inputs.property("holidayApiKey", holidayKey)
    inputs.property("supabaseUrl", sbUrl)
    inputs.property("supabasePublishableKey", sbKey)
    inputs.property("kakaoNativeAppKey", kakaoKey)
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

                /** 카카오 네이티브 앱 키. Android SDK 초기화 + kakao{key}://oauth 스킴. local.properties `kakao.native.app.key`. */
                const val KakaoNativeAppKey: String = "$kakaoKey"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
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
            implementation(libs.kakao.user)
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
