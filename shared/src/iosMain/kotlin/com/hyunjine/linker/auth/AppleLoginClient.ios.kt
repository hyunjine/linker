package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS Apple Sign-In. 실제 SDK 호출은 Swift 쪽 [AppleLoginBridge.handler] 가 담당한다.
 * iOSApp.swift 에서 앱 시작 시 handler 를 세팅하지 않으면 로그인 시도가 [AppleLoginResult.Failure] 반환.
 */
actual class AppleLoginClient {
    actual suspend fun login(): AppleLoginResult = suspendCancellableCoroutine { cont ->
        val handler = AppleLoginBridge.handler
        if (handler == null) {
            if (cont.isActive) cont.resume(
                AppleLoginResult.Failure("AppleLoginBridge.handler not configured (Swift 초기화 확인)"),
            )
            return@suspendCancellableCoroutine
        }
        handler { result ->
            if (cont.isActive) cont.resume(result)
        }
    }
}

@Composable
actual fun rememberAppleLoginClient(): AppleLoginClient =
    remember { AppleLoginClient() }

actual val supportsAppleSignIn: Boolean = true
