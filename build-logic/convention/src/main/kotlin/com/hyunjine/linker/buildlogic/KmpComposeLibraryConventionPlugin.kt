package com.hyunjine.linker.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `linker.kmp.compose.library` — Compose Multiplatform 을 쓰는 KMP UI 모듈 컨벤션.
 * `linker.kmp.library` + Compose Multiplatform + Compose Compiler 플러그인 적용.
 *
 * 공용 dep (compose runtime/foundation/material3/ui 등) 은 각 모듈의 `commonMain.dependencies`
 * 에서 카탈로그로 참조 (컨벤션에서 자동 주입은 KotlinMultiplatformExtension.sourceSets 접근
 * 이슈로 보류).
 */
class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("linker.kmp.library")
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
    }
}
