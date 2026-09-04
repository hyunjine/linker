package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android stub. Apple Sign-In 은 Android 네이티브 SDK 가 없어 Supabase OAuth 웹 리다이렉트
 * (Custom Tabs) 로 구현해야 하며, 후속 PR 에서 다룬다. 지금은 항상 Failure 반환.
 *
 * [supportsAppleSignIn] 이 false 라 LoginScreen 에 Apple 버튼도 노출되지 않아 실제로 이 login()
 * 이 호출될 경로가 없다 — safety net.
 */
actual class AppleLoginClient {
    actual suspend fun login(): AppleLoginResult =
        AppleLoginResult.Failure("Android 에서는 아직 Apple Sign-In 미지원")
}

@Composable
actual fun rememberAppleLoginClient(): AppleLoginClient =
    remember { AppleLoginClient() }

actual val supportsAppleSignIn: Boolean = false
