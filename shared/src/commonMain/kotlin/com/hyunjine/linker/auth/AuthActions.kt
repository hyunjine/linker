package com.hyunjine.linker.auth

import com.hyunjine.linker.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

/**
 * Apple Sign-In → Supabase `signInWith(IDToken, Apple, nonce)` 흐름.
 *
 * 1. [client]. login() → 네이티브 Apple Sign-In 다이얼로그 (iOS only, Android 는 미지원 stub)
 * 2. 성공 시 id_token + raw nonce 를 Supabase 로 전달
 * 3. Supabase 서버가 id_token 서명 · aud · SHA256(rawNonce) == nonce claim 검증 → 세션 발급
 *
 * 카카오와 달리 nonce 를 반드시 함께 넘겨야 Supabase 가 검증을 통과시킨다.
 * 취소는 조용히 무시. 실패는 UI 층에서 catch 해 처리.
 */
suspend fun signInWithApple(client: AppleLoginClient) {
    println("[Auth] signInWithApple: enter")
    when (val result = client.login()) {
        is AppleLoginResult.Success -> {
            println("[Auth] Apple 로그인 성공 → Supabase signInWithIdToken")
            SupabaseProvider.client.auth.signInWith(IDToken) {
                idToken = result.idToken
                provider = Apple
                nonce = result.rawNonce
            }
            println("[Auth] Supabase 세션 발급 완료 (Apple)")

            // Apple 은 최초 로그인 시에만 fullName 을 credential 로 넘긴다. 있으면 Supabase
            // user_metadata 에 저장해 ProfileSetupScreen 프리필 (profileDefaults) 이 사용하도록.
            // 이후 재로그인 시엔 Apple 이 nil 을 반환하지만 이미 저장된 metadata 가 있어 문제없음.
            val name = result.fullName
            if (!name.isNullOrBlank()) {
                runCatching {
                    SupabaseProvider.client.auth.updateUser {
                        data { put("full_name", JsonPrimitive(name)) }
                    }
                    println("[Auth] Apple user_metadata.full_name = $name 저장 완료")
                }.onFailure {
                    println("[Auth] Apple user_metadata 저장 실패 (무시): $it")
                }
            }
        }
        AppleLoginResult.Cancelled -> {
            println("[Auth] Apple 로그인 사용자 취소")
        }
        is AppleLoginResult.Failure -> {
            println("[Auth] Apple 로그인 실패: ${result.reason}")
            error(result.reason)
        }
    }
}

/**
 * Google Sign-In → Supabase `signInWith(IDToken, Google, nonce)` 흐름.
 *
 * 1. [client]. login() → 네이티브 Google SDK (iOS GIDSignIn · Android Credential Manager)
 * 2. 성공 시 id_token + raw nonce 를 Supabase 로 전달
 * 3. Supabase 서버가 Google JWKS 로 id_token 서명·aud·SHA256(rawNonce)==nonce claim 검증 → 세션 발급
 *
 * Google 은 이름·이메일·프로필 사진을 id_token 에 담아준다. Supabase 가 자동으로
 * user_metadata (name, avatar_url) 로 매핑해줘 별도 updateUser 호출 불필요 — profileDefaults()
 * 가 그대로 프리필.
 */
suspend fun signInWithGoogle(client: GoogleLoginClient) {
    println("[Auth] signInWithGoogle: enter")
    when (val result = client.login()) {
        is GoogleLoginResult.Success -> {
            println("[Auth] Google 로그인 성공 → Supabase signInWithIdToken")
            SupabaseProvider.client.auth.signInWith(IDToken) {
                idToken = result.idToken
                provider = Google
                nonce = result.rawNonce
            }
            println("[Auth] Supabase 세션 발급 완료 (Google)")
        }
        GoogleLoginResult.Cancelled -> {
            println("[Auth] Google 로그인 사용자 취소")
        }
        is GoogleLoginResult.Failure -> {
            println("[Auth] Google 로그인 실패: ${result.reason}")
            error(result.reason)
        }
    }
}

/**
 * DEBUG 빌드 전용: Supabase 대시보드에서 만든 테스트 유저로 email/password 로그인.
 * 카카오 계정이 하나뿐이라 커플·Realtime 등 두 유저 흐름을 검증할 때 사용.
 *
 * Release 빌드에서는 UI 자체가 노출되지 않으므로 호출 경로가 없다.
 * 실패 시 예외를 던져 상위 catch 로 UI 에 표시.
 */
suspend fun signInWithEmail(email: String, password: String) {
    println("[Auth] signInWithEmail: $email")
    SupabaseProvider.client.auth.signInWith(Email) {
        this.email = email
        this.password = password
    }
    println("[Auth] Supabase 세션 발급 완료 (email)")
}

/**
 * Supabase Auth 세션 상태 스트림. [SessionStatus.Authenticated] 로 넘어가면 로그인 완료.
 * 앱 시작 시 저장된 세션 복원 중이면 [SessionStatus.Initializing] / [SessionStatus.NotAuthenticated] 를 거친다.
 */
val sessionStatus: StateFlow<SessionStatus>
    get() = SupabaseProvider.client.auth.sessionStatus

/**
 * 로그아웃. Supabase 세션 폐기 → `sessionStatus` NotAuthenticated → App 레벨
 * LaunchedEffect 가 LoginRoute 로 리라우팅.
 */
suspend fun signOut() {
    println("[Auth] signOut: enter")
    SupabaseProvider.client.auth.signOut()
    println("[Auth] Supabase signOut 완료")
}

/**
 * 서버에서 이미 무효가 된 세션을 로컬에서만 지운다 (#437). 저장된 세션 삭제 · 자동 갱신 중단 →
 * `sessionStatus` NotAuthenticated → App 레벨 LaunchedEffect 가 로그인으로 리셋.
 *
 * [signOut] 은 서버 `/logout` 호출에 만료된 access token 을 갱신하려다 실패하면 (`TokenExpiredException`)
 * 로컬 세션을 지우지 못한 채 예외로 끝나서, refresh token 이 거부된 세션 정리에는 쓰지 않는다.
 * supabase-kt 도 자동 갱신에서 거부 응답을 받으면 같은 `clearSession` 으로 정리한다.
 *
 * @param reason 로그에 남길 정리 사유.
 */
suspend fun clearLocalSession(reason: String) {
    println("[Auth] 로컬 세션 정리: $reason")
    SupabaseProvider.client.auth.clearSession()
}

/**
 * 자동 토큰 갱신을 현재 세션 기준으로 다시 건다 (#437). 갱신 예약 시각을 지금 시계로 다시 계산하고,
 * 이미 만료 임박이면 즉시 갱신 (거부 응답이면 supabase-kt 가 세션을 지움 · 네트워크 오류면 재시도).
 *
 * iOS 는 supabase-kt 가 백그라운드 전환을 처리하지 않아, 앱이 멈춰 있던 동안 갱신 예약이 밀릴 수 있다.
 * `Authenticated` 가 아니면 (갱신 재시도 중 · 로그아웃) 아무것도 하지 않는다.
 */
suspend fun restartSessionAutoRefresh() {
    val auth = SupabaseProvider.client.auth
    if (auth.sessionStatus.value !is SessionStatus.Authenticated) return
    runCatching { auth.startAutoRefreshForCurrentSession() }
        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        .onFailure { println("[Auth] 자동 갱신 재시작 실패: $it") }
}
