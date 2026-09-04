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
        // Kakao SDK 는 Maven Central 이 아니라 자체 nexus 에만 배포된다.
        maven { url = java.net.URI("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

include(":androidApp")
include(":shared")