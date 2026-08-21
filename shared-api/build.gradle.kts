import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * shared-api — 서버와 클라이언트가 함께 쓰는 순수 DTO/enum 모듈.
 *  - Compose · Ktor · Exposed 등 무거운 의존은 절대 넣지 않는다 (이 모듈이 그걸 끌고 오면
 *    :server 도 UI 의존을 가져가게 됨).
 *  - kotlinx.serialization + kotlinx.datetime 만 사용.
 *  - JVM (:server), Android (:shared → :androidApp), iOS (:shared → framework) 3 타겟.
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
