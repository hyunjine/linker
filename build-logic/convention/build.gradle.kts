plugins {
    `kotlin-dsl`
}

group = "com.hyunjine.linker.buildlogic"

// Gradle 8+ 는 kotlin-dsl 이 JVM 17 로 컴파일된 코드를 요구.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // 컨벤션 플러그인이 KotlinMultiplatformExtension · KotlinMultiplatformAndroidLibraryExtension ·
    // ComposeExtension 등 DSL 타입을 참조하려면 해당 플러그인 jar 가 compileOnly 로 필요.
    // 런타임은 컨벤션 소비 모듈이 이미 로드하므로 불필요.
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.composeCompiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "linker.kmp.library"
            implementationClass = "com.hyunjine.linker.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpComposeLibrary") {
            id = "linker.kmp.compose.library"
            implementationClass = "com.hyunjine.linker.buildlogic.KmpComposeLibraryConventionPlugin"
        }
    }
}
