package com.hyunjine.linker.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `linker.kmp.library` — Compose 없는 순수 KMP 라이브러리 모듈용 컨벤션.
 *
 * 자동 적용:
 * - Kotlin Multiplatform + Android Multiplatform Library 플러그인
 * - Android 타겟: namespace 만 소비 모듈이 직접 지정, compileSdk/minSdk/JVM 은 여기서 통일
 * - iOS 타겟: arm64 · simulatorArm64 (기본 framework 는 소비 모듈이 필요 시 추가)
 *
 * 사용:
 * ```
 * plugins {
 *     id("linker.kmp.library")
 * }
 * kotlin {
 *     androidLibrary { namespace = "com.hyunjine.linker.data" }
 * }
 * ```
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()
            // Android 타겟은 KotlinMultiplatformAndroidLibraryExtension 로 별도 세팅.
        }

        extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
            compileSdk = libs.findVersion("android-compileSdk").get().toString().toInt()
            minSdk = libs.findVersion("android-minSdk").get().toString().toInt()
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }
}
