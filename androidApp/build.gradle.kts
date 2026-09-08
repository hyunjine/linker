import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

// 앱 버전은 `iosApp/Configuration/Config.xcconfig` 의 MARKETING_VERSION 을 단일 소스로 함.
// iOS 는 이 값을 xcconfig 로 네이티브 사용, Android 는 여기서 파싱해 versionName 에 매핑.
// versionCode 는 semver 를 정수로 변환 (10000·100·1 자리) — Play Store 는 versionCode 가 항상
// 증가해야 하므로, marketing 이 항상 올라가는 한 이 정수도 자동으로 증가한다.
private val appVersionName: String = rootProject.file("iosApp/Configuration/Config.xcconfig")
    .readLines()
    .firstOrNull { it.trim().startsWith("MARKETING_VERSION=") }
    ?.substringAfter("=")
    ?.trim()
    ?: error("Config.xcconfig 에 MARKETING_VERSION= 라인이 없음")
private val appVersionCode: Int = appVersionName
    .split(".")
    .let { parts ->
        require(parts.size == 3) { "MARKETING_VERSION 은 major.minor.patch 형식이어야 함 (현재: $appVersionName)" }
        val (major, minor, patch) = parts.map(String::toInt)
        require(minor in 0..99 && patch in 0..99) { "minor · patch 는 0..99 (현재: $appVersionName)" }
        major * 10000 + minor * 100 + patch
    }

kotlin {
    // shared 모듈과 동일. Foojay resolver 가 로컬에 없으면 JDK 21 자동 다운로드.
    jvmToolchain(21)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Firebase Cloud Messaging (파트너 스케줄 알림 수신)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}

android {
    namespace = "com.hyunjine.linker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hyunjine.linker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}