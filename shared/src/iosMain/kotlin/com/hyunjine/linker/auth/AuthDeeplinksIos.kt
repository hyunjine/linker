package com.hyunjine.linker.auth

import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks
import platform.Foundation.NSURL

/**
 * Supabase Auth OAuth 콜백 딥링크를 처리해 세션에 반영한다.
 * Swift 의 `.onOpenURL` 에서 호출. supabase-kt 타입을 iOSApp 에 노출하지 않도록 감싼다.
 *
 * try-catch 는 Kotlin/Native → Swift 상호운용 필수: @Throws 없이 예외를 던지면
 * Kotlin_ObjCExport_trapOnUndeclaredException 이 프로세스를 즉시 종료시킨다.
 * onSessionSuccess/onError 는 named 로 전달 (trailing lambda 는 onError 에 붙어버림).
 */
fun handleAuthDeeplinks(url: NSURL) {
    println("[Auth] handleAuthDeeplinks: url=${url.absoluteString}")
    try {
        SupabaseProvider.client.handleDeeplinks(
            url = url,
            onSessionSuccess = { session ->
                println("[Auth] deeplink onSessionSuccess: user=${session.user?.id}")
            },
            onError = { t ->
                println("[Auth] deeplink onError: ${t.message}")
                t.printStackTrace()
            },
        )
        println("[Auth] handleAuthDeeplinks: 정상 반환")
    } catch (t: Throwable) {
        println("[Auth] handleAuthDeeplinks threw ${t::class.simpleName}: ${t.message}")
        t.printStackTrace()
    }
}
