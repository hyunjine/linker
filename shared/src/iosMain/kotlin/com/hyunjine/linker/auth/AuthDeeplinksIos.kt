package com.hyunjine.linker.auth

import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks
import platform.Foundation.NSURL

/**
 * Supabase Auth OAuth 콜백 딥링크를 처리해 세션에 반영한다.
 * Swift 의 `.onOpenURL` 에서 호출. supabase-kt 타입을 iOSApp 에 노출하지 않도록 감싼다.
 */
fun handleAuthDeeplinks(url: NSURL) {
    println("[Auth] handleAuthDeeplinks: url=${url.absoluteString}")
    SupabaseProvider.client.handleDeeplinks(url) { session ->
        println("[Auth] deeplink onSessionSuccess: user=${session.user?.id}")
    }
}
