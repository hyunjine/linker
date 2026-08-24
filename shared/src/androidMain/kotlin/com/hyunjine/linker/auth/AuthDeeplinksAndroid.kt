package com.hyunjine.linker.auth

import android.content.Intent
import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks

/**
 * Supabase Auth OAuth 콜백 딥링크를 처리해 세션에 반영한다.
 * [MainActivity.onNewIntent] 에서 호출. supabase-kt 타입을 androidApp 에 노출하지 않도록
 * shared 안에서 감싼다.
 */
fun handleAuthDeeplinks(intent: Intent) {
    SupabaseProvider.client.handleDeeplinks(intent)
}
