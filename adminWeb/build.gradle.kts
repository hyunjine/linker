plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :adminWeb — Compose Multiplatform · Kotlin/Wasm 브라우저 타깃.
// docs/261-admin-push-console.md §5 — 관리자 푸시 발송 콘솔의 Gradle 부트스트랩.
// 현재 범위는 부트스트랩만: 브라우저에서 "Linker · 관리자 콘솔" placeholder 를 렌더.
// `:shared` 의존은 shared 모듈이 wasmJs 타깃을 갖도록 확장된 뒤 후속 이슈에서 연결.

kotlin {
    // shared / androidApp 과 동일. Foojay 리졸버가 JDK 21 을 자동 조달.
    jvmToolchain(21)

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("adminWeb")
        browser {
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "adminWeb.js"
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    // dev 서버가 index.html 을 서빙할 정적 리소스 루트.
                    static(projectDirPath + "/src/wasmJsMain/resources")
                }
            }
        }
        binaries.executable()
    }

    sourceSets.named("wasmJsMain") {
        dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.hyunjine.linker.adminweb.resources"
    generateResClass = auto
}
