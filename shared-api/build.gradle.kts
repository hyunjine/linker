import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * shared-api — Supabase (PostgREST · Realtime) 응답을 매핑하는 순수 DTO/enum 모듈.
 *  - Compose 등 무거운 UI 의존은 절대 넣지 않는다 (계약만 담는 얇은 레이어 유지).
 *  - kotlinx.serialization + kotlinx.datetime 만 사용.
 *  - Android (:shared → :androidApp), iOS (:shared → framework), JVM (테스트/스크립트) 3 타겟.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        // iOS framework 는 :shared 가 이 모듈을 export 하도록 설정. 여기서는 별도 binary 만들지 않음.
    }

    android {
        namespace = "com.hyunjine.linker.api"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
