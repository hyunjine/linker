package com.hyunjine.linker.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `linker.kmp.compose.library` — Compose Multiplatform 을 쓰는 KMP UI 모듈 컨벤션.
 *
 * 순수 KMP 컨벤션 ([KmpLibraryConventionPlugin]) 위에 Compose Multiplatform + Compose Compiler
 * 플러그인을 얹고, `commonMain` 에 Compose 런타임/foundation/material3/ui 를 자동 주입.
 * 개별 모듈은 자기 feature 관련 dep 만 추가.
 *
 * 사용:
 * ```
 * plugins {
 *     id("linker.kmp.compose.library")
 * }
 * kotlin {
 *     androidLibrary { namespace = "com.hyunjine.linker.feature.main" }
 * }
 * ```
 */
class KmpComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        // Base KMP library setup 재사용.
        pluginManager.apply("linker.kmp.library")

        with(pluginManager) {
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                fun ver(alias: String) = libs.findLibrary(alias).get()
                implementation(ver("compose-runtime"))
                implementation(ver("compose-foundation"))
                implementation(ver("compose-material3"))
                implementation(ver("compose-ui"))
                implementation(ver("compose-components-resources"))
                implementation(ver("compose-uiToolingPreview"))
                implementation(ver("androidx-lifecycle-viewmodelCompose"))
                implementation(ver("androidx-lifecycle-runtimeCompose"))
            }
        }
    }
}
