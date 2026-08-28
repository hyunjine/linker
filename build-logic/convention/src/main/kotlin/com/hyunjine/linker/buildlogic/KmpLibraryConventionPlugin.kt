package com.hyunjine.linker.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `linker.kmp.library` — 순수 KMP 라이브러리 모듈용 컨벤션.
 *
 * 플러그인만 적용. KMP 타겟 (iOS · android) · compileSdk/minSdk · JVM target 은
 * 각 모듈의 `kotlin { ... }` 블록에서 설정 (AGP KMP DSL 타입이 아직 확정 안 돼 있고
 * 컨벤션에서 configure 하기 어려움).
 *
 * 사용:
 * ```
 * plugins { id("linker.kmp.library") }
 * kotlin {
 *     iosArm64(); iosSimulatorArm64()
 *     android { namespace = "..."; compileSdk = ...; minSdk = ... }
 *     sourceSets { commonMain.dependencies { ... } }
 * }
 * ```
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
        }
    }
}
