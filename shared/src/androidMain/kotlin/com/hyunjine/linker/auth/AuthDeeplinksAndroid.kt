package com.hyunjine.linker.auth

import android.content.Intent
import android.util.Log
import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks

/**
 * Supabase Auth OAuth 콜백 딥링크를 처리해 세션에 반영한다.
 * [MainActivity.onNewIntent] 에서 호출. supabase-kt 타입을 androidApp 에 노출하지 않도록
 * shared 안에서 감싼다.
 *
 * onSessionSuccess/onError 는 named 로 전달 — 그러지 않으면 trailing lambda 가
 * 마지막 함수 파라미터인 onError 에 붙어 UserSession 이 아니라 Throwable 로 잡힌다.
 */
fun handleAuthDeeplinks(intent: Intent) {
    Log.d("Auth", "handleAuthDeeplinks: action=${intent.action} data=${intent.data}")
    try {
        SupabaseProvider.client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { session ->
                Log.d("Auth", "deeplink onSessionSuccess: user=${session.user?.id}")
            },
            onError = { t ->
                Log.e("Auth", "deeplink onError", t)
            },
        )
        Log.d("Auth", "handleAuthDeeplinks: 정상 반환")
    } catch (t: Throwable) {
        Log.e("Auth", "handleAuthDeeplinks threw", t)
    }
}
