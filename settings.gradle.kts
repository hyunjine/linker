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