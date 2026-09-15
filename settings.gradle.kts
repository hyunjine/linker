rootProject.name = "Linker"

pluginManagement {
    // 컨벤션 플러그인 (linker.kmp.library · linker.kmp.compose.library) 을 참조 가능하게
    // build-logic 을 composite build 로 include. #96 멀티모듈 리팩터의 인프라 조각.
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Kotlin JVM Toolchain 이 요구하는 JDK 를 Adoptium/Foojay 에서 자동 다운로드해준다.
// jvmToolchain(21) 을 선언한 모듈은 로컬에 openjdk 가 없어도 gradle 이 알아서 조달 →
// Xcode Cloud · 새 팀원 온보딩 등에서 "JDK 버전 안 맞음" 문제 해결.
// pluginManagement 블록보다 뒤에 와야 함 (gradle 규정).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // MSAL 이 transitive 로 Surface Duo 전용 `com.microsoft.device.display:display-mask`
        // 를 요구하는데 Maven Central 미배포 · Microsoft 공식 Duo SDK 저장소에만 있음.
        // 범위는 그 그룹 한정으로 좁혀 다른 dep 해석 속도에는 영향 없게.
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
            mavenContent {
                includeGroupAndSubgroups("com.microsoft.device")
            }
        }
    }
}

include(":androidApp")
include(":shared")