package com.hyunjine.linker.platform

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Android 는 [FirebaseMessaging] SDK 가 이미 캐시하고 있는 현재 FCM 토큰을 fetch 해
 * [FcmTokenBridge] 로 upsert 를 트리거한다.
 *
 * `onNewToken` 은 토큰이 **새로 발급 · rotate** 될 때만 fire 되므로, 앱을 죽이지 않고
 * 계정 스왑 (test1 로그아웃 → test2 로그인) 하면 캐시된 동일 토큰이라 delegate 가 재fire
 * 되지 않는다 → test2 user_devices row 미생성. 로그인 성공 시점에 이 함수를 명시적으로
 * 호출해 그 gap 을 메꾼다.
 *
 * fetch 실패는 대개 Google Play services 미탑재 · 네트워크 없음 등 재시도 여지 있는 상황.
 * 로그만 남기고 조용히 통과 — 다음 로그인 · 앱 재실행 시 onNewToken 이 자연스레 복구.
 */
actual fun ensureCurrentDeviceRegistered() {
    runCatching {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "getToken 성공했지만 token 이 빈 값 — 스킵")
                    return@addOnSuccessListener
                }
                Log.d(TAG, "ensure token upsert: ${token.take(12)}…")
                FcmTokenBridge.onTokenRefreshedAsync(token, platform = "android")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "FCM token fetch 실패 (다음 트리거 시 자동 복구): ${e.message}")
            }
    }.onFailure { Log.w(TAG, "ensureCurrentDeviceRegistered 예외: ${it.message}") }
}

private const val TAG = "LinkerDeviceRegister"
