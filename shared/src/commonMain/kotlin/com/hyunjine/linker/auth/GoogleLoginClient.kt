package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable

/**
 * Google Sign-In 결과. 성공 시 [Success.idToken] 을 Supabase
 * `signInWithIdToken(Google, idToken, nonce)` 로 전달한다.
 *
 * Apple 과 마찬가지로 **nonce 검증** 을 사용 — 클라이언트가 raw nonce 를 만들고 SHA256(raw)
 * 을 Google 요청 시 전달하면 id_token 의 nonce claim 에 그 해시가 담긴다. Supabase 가 raw
 * nonce 를 다시 SHA256 해 검증. Supabase 의 Google provider 에서 "Skip nonce checks" 가
 * OFF (기본값 · 권장) 이면 nonce 없이 보내면 거부됨.
 */
sealed interface GoogleLoginResult {
    /**
     * @param idToken Google 이 발급한 OIDC id_token (JWT). Supabase 로 그대로 전달.
     * @param rawNonce Google 요청 시 클라이언트가 생성했던 원본 nonce. Supabase 검증용.
     */
    data class Success(val idToken: String, val rawNonce: String) : GoogleLoginResult

    /** 사용자 취소 (다이얼로그 dismiss · 뒤로가기). 로그인 화면에서 조용히 무시. */
    data object Cancelled : GoogleLoginResult

    /** SDK/네트워크/구글 서버 오류. [reason] 은 개발자 로그용. */
    data class Failure(val reason: String) : GoogleLoginResult
}

/**
 * Google Sign-In 실행자. 플랫폼별 actual 이 실제 SDK 호출을 담당.
 *
 * - iOS: `google-signin-ios` SPM 의 `GIDSignIn.sharedInstance.signIn(withPresenting:hint:additionalScopes:nonce:)`
 *   호출을 Swift 브리지가 수행.
 * - Android: `androidx.credentials` (Credential Manager) 의 `GetGoogleIdOption` 사용.
 */
expect class GoogleLoginClient {
    suspend fun login(): GoogleLoginResult
}

/** Compose 트리에서 플랫폼 컨텍스트를 주입해 [GoogleLoginClient] 생성. */
@Composable
expect fun rememberGoogleLoginClient(): GoogleLoginClient
