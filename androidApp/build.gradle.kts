import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

// 카카오 네이티브 앱 키를 local.properties → manifest placeholder 로 주입.
// AndroidManifest 의 kakao${KAKAO_NATIVE_APP_KEY}://oauth 스킴에 사용.
private val kakaoNativeAppKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("kakao.native.app.key", "")

// 앱 버전은 루트 VERSION 파일이 유일 소스. 파일 첫 줄이 semver (`major.minor.patch`),
// 그 아래 라인들은 GitHub Release 노트 본문 (릴리즈 워크플로 #184 가 사용).
// Xcode Cloud ci_post_clone.sh 도 첫 줄을 읽어 MARKETING_VERSION 을 세팅한다.
// versionCode 는 semver 를 정수로 변환 (10000·100·1 자리) — Play Store 는 versionCode 가 항상
// 증가해야 하므로, VERSION 파일이 항상 올라가는 한 이 정수도 자동으로 증가한다.
private val appVersionName: String = rootProject.file("VERSION")
    .readLines()
    .firstOrNull { it.isNotBlank() }
    ?.trim()
    ?: error("VERSION 파일이 비어있음")
private val appVersionCode: Int = appVersionName
    .split(".")
    .let { parts ->
        require(parts.size == 3) { "VERSION 은 major.minor.patch 형식이어야 함 (현재: $appVersionName)" }
        val (major, minor, patch) = parts.map(String::toInt)
        require(minor in 0..99 && patch in 0..99) { "minor · patch 는 0..99 (현재: $appVersionName)" }
        major * 10000 + minor * 100 + patch
    }

kotlin {
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

        // Kakao SDK 콜백 스킴 kakao{키}://oauth 의 {키} 자리에 주입.
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
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