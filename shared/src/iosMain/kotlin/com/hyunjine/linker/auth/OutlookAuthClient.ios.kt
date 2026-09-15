package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS MSAL 구현. 실제 SDK 호출은 Swift [OutlookAuthBridge] handler 가 수행하고, 여기서는
 * callback → suspend 변환만 담당한다. handler 미세팅 (예: SPM 아직 안 붙였을 때) 상태에서는
 * 안전하게 Failure/null 반환.
 *
 * 앱 부트스트랩 (iOSApp.swift) 이 4개 handler 를 모두 세팅해야 정상 동작. handler 미세팅 상황을
 * 감지하기 위해 login 은 Failure(reason) 로 로그 남기고, 나머지는 조용히 null 반환.
 */
actual class OutlookAuthClient {
    actual suspend fun login(): OutlookAuthResult {
        val handler = OutlookAuthBridge.loginHandler
        if (handler == null) {
            println("[OutlookAuth iOS] loginHandler 미세팅 — iOSApp.swift init 실행 여부 확인 필요")
            return OutlookAuthResult.Failure("OutlookAuthBridge.loginHandler 미세팅")
        }
        println("[OutlookAuth iOS] loginHandler 호출 — Swift OutlookAuthProvider 로 위임")
        return suspendCancellableCoroutine { cont ->
            handler { result -> cont.resume(result) }
        }
    }

    actual suspend fun signOut() {
        val handler = OutlookAuthBridge.signOutHandler ?: return
        suspendCancellableCoroutine<Unit> { cont ->
            handler { cont.resume(Unit) }
        }
    }

    actual suspend fun currentAccount(): OutlookAccount? {
        val handler = OutlookAuthBridge.currentAccountHandler ?: return null
        return suspendCancellableCoroutine { cont ->
            handler { account -> cont.resume(account) }
        }
    }

    actual suspend fun accessToken(): String? {
        val handler = OutlookAuthBridge.accessTokenHandler ?: return null
        return suspendCancellableCoroutine { cont ->
            handler { token -> cont.resume(token) }
        }
    }
}

@Composable
actual fun rememberOutlookAuthClient(): OutlookAuthClient =
    remember { OutlookAuthClient() }
