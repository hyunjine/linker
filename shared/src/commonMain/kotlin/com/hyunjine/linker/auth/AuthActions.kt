package com.hyunjine.linker.auth

import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Kakao
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * 카카오 SDK 로그인 → Supabase `signInWith(IDToken)` 흐름.
 *
 * 1. [client]. login() → 네이티브 SDK 로 로그인 (카톡 앱 · 계정 웹 로그인 자동 폴백)
 * 2. 성공 시 [KakaoLoginResult.Success.idToken] 을 Supabase Auth 로 전달
 * 3. Supabase 서버가 JWKS 로 서명 · 만료 · aud 검증 → 세션 발급
 *
 * 취소는 조용히 무시. 실패/예외는 UI 층에서 catch 해 처리.
 *
 * @throws IllegalStateException Kakao 결과가 Failure 인 경우 (reason 을 메시지로).
 */
suspend fun signInWithKakao(client: KakaoLoginClient) {
    println("[Auth] signInWithKakao: enter")
    when (val result = client.login()) {
        is KakaoLoginResult.Success -> {
            println("[Auth] Kakao 로그인 성공 → Supabase signInWithIdToken")
            SupabaseProvider.client.auth.signInWith(IDToken) {
                idToken = result.idToken
                provider = Kakao
            }
            println("[Auth] Supabase 세션 발급 완료")
        }
        KakaoLoginResult.Cancelled -> {
            println("[Auth] Kakao 로그인 사용자 취소")
        }
        is KakaoLoginResult.Failure -> {
            println("[Auth] Kakao 로그인 실패: ${result.reason}")
            error(result.reason)
        }
    }
}

/**
 * Supabase Auth 세션 상태 스트림. [SessionStatus.Authenticated] 로 넘어가면 로그인 완료.
 * 앱 시작 시 저장된 세션 복원 중이면 [SessionStatus.Initializing] / [SessionStatus.NotAuthenticated] 를 거친다.
 */
val sessionStatus: StateFlow<SessionStatus>
    get() = SupabaseProvider.client.auth.sessionStatus

/**
 * 로그아웃. Kakao SDK 세션 → Supabase 세션 순서로 폐기.
 *
 * Kakao 를 먼저 폐기해야 다음 로그인 시 계정 선택 화면이 다시 뜬다 (안 그러면 캐시된 카카오
 * 세션으로 조용히 재로그인). Supabase signOut 은 sessionStatus 를 NotAuthenticated 로
 * 바꿔 App 레벨 LaunchedEffect 가 LoginRoute 로 리라우팅한다.
 */
suspend fun signOut(client: KakaoLoginClient) {
    println("[Auth] signOut: enter")
    runCatching { client.logout() }
        .onFailure { println("[Auth] Kakao logout 실패 (무시): $it") }
    SupabaseProvider.client.auth.signOut()
    println("[Auth] Supabase signOut 완료")
}
