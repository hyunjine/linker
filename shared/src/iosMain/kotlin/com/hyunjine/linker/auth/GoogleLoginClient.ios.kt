package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS Google Sign-In. 실제 SDK 호출은 Swift 쪽 [GoogleLoginBridge.handler] 가 담당한다.
 * iOSApp.swift 에서 handler 세팅 안 되어 있으면 [GoogleLoginResult.Failure] 반환.
 */
actual class GoogleLoginClient {
    actual suspend fun login(): GoogleLoginResult = suspendCancellableCoroutine { cont ->
        val handler = GoogleLoginBridge.handler
        if (handler == null) {
            if (cont.isActive) cont.resume(
                GoogleLoginResult.Failure("GoogleLoginBridge.handler not configured (Swift 초기화 확인)"),
            )
            return@suspendCancellableCoroutine
        }
        handler { result ->
            if (cont.isActive) cont.resume(result)
        }
    }
}

@Composable
actual fun rememberGoogleLoginClient(): GoogleLoginClient =
    remember { GoogleLoginClient() }
