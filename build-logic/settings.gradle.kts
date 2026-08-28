// build-logic 는 root 프로젝트와 분리된 composite build 로 참여.
// root 의 `libs.versions.toml` 을 재사용해 컨벤션 플러그인 안에서 같은 버전 카탈로그 접근.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
