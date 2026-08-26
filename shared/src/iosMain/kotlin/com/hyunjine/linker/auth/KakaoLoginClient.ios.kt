package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * iOS 카카오 로그인. 실제 SDK 호출은 Swift 쪽 [KakaoLoginBridge.handler] 가 담당한다.
 * iOSApp.swift 에서 앱 시작 시 handler 를 세팅하지 않으면 로그인 시도가 [KakaoLoginResult.Failure] 반환.
 */
actual class KakaoLoginClient {
    actual suspend fun login(): KakaoLoginResult = suspendCancellableCoroutine { cont ->
        val handler = KakaoLoginBridge.handler
        if (handler == null) {
            if (cont.isActive) cont.resume(
                KakaoLoginResult.Failure("KakaoLoginBridge.handler not configured (Swift 초기화 확인)"),
            )
            return@suspendCancellableCoroutine
        }
        handler { result ->
            if (cont.isActive) cont.resume(result)
        }
    }
}

@Composable
actual fun rememberKakaoLoginClient(): KakaoLoginClient =
    remember { KakaoLoginClient() }
