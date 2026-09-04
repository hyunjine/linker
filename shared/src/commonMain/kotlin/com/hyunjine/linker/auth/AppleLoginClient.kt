package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable

/**
 * Apple Sign-In (Sign in with Apple) 결과. 성공 시 [Success.idToken] 을 Supabase
 * `signInWithIdToken(Apple, idToken, nonce)` 로 전달한다.
 *
 * 카카오와 달리 Apple 은 **nonce 검증이 필수** — 클라이언트가 raw nonce 를 만들고
 * SHA256(rawNonce) 를 Apple 에 요청해 id_token 에 그 해시가 담긴다. Supabase 는
 * 여기서 넘긴 raw nonce 를 다시 SHA256 하여 id_token 의 nonce claim 과 비교.
 * 따라서 [Success.rawNonce] 도 반드시 함께 넘긴다.
 */
sealed interface AppleLoginResult {
    /**
     * @param idToken Apple 이 발급한 OIDC id_token (JWT). Supabase 로 그대로 전달.
     * @param rawNonce Apple 요청 시 클라이언트가 생성했던 원본 nonce. Supabase 검증용.
     * @param fullName Apple 이 credential 에 담아준 성+이름 문자열. **최초 로그인 시에만** null 아님
     *  (Apple 프라이버시 정책 — 재로그인 시 nil). 있으면 Supabase user_metadata 로 저장해 이후
     *  ProfileSetupScreen 프리필에 사용.
     */
    data class Success(
        val idToken: String,
        val rawNonce: String,
        val fullName: String? = null,
    ) : AppleLoginResult

    /** 사용자 취소 (다이얼로그 dismiss · 뒤로가기). 로그인 화면에서 조용히 무시. */
    data object Cancelled : AppleLoginResult

    /** Apple 서비스/네트워크/브리지 오류. [reason] 은 개발자 로그용. */
    data class Failure(val reason: String) : AppleLoginResult
}

/**
 * Apple Sign-In 실행자. 플랫폼별 actual 이 실제 처리를 담당.
 *
 * - iOS: `ASAuthorizationController` 를 Swift 쪽 브리지에서 호출. nonce 생성/SHA256
 *   해싱 · 결과 매핑도 Swift 가 처리.
 * - Android: 현재 stub — Apple 은 Android 에서 Supabase OAuth 웹 리다이렉트 방식으로
 *   지원해야 하며 별도 PR 에서 다룬다. 지금은 [AppleLoginResult.Failure] 반환.
 */
expect class AppleLoginClient {
    suspend fun login(): AppleLoginResult
}

/** Compose 트리에서 플랫폼 컨텍스트를 주입해 [AppleLoginClient] 생성. */
@Composable
expect fun rememberAppleLoginClient(): AppleLoginClient

/**
 * 현재 플랫폼이 네이티브 Apple Sign-In 을 지원하는지 여부.
 * true 인 플랫폼에서만 LoginScreen 에 Apple 버튼을 노출한다.
 */
expect val supportsAppleSignIn: Boolean
