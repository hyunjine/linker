package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable

/**
 * 카카오 SDK 로그인 결과. 성공 시 [Success.idToken] 을 Supabase `signInWithIdToken(Kakao, idToken)`
 * 에 그대로 넘긴다. `access_token` 은 Supabase 검증에 불필요해 담지 않음.
 */
sealed interface KakaoLoginResult {
    /**
     * OIDC id_token (JWT). Supabase 서버가 카카오 JWKS 로 서명 · 만료 · aud 를 검증해 세션 발급.
     * OIDC 활성화 안 된 앱에선 null 이 와서 [Failure] 로 매핑됨 ([KakaoLoginClient] 구현체 책임).
     */
    data class Success(val idToken: String) : KakaoLoginResult

    /** 사용자 취소 (뒤로가기 등). 로그인 화면에서 조용히 무시. */
    data object Cancelled : KakaoLoginResult

    /** SDK/네트워크/카카오 서버 오류. [reason] 은 개발자 로그용. UI 에는 표준 문구. */
    data class Failure(val reason: String) : KakaoLoginResult
}

/**
 * 카카오 SDK 로그인 실행자. 플랫폼별 actual 이 실제 SDK 호출을 담당.
 *
 * - Android: 카카오톡 설치 시 톡 로그인, 없으면 카카오 계정 웹 로그인.
 * - iOS: 동일 정책 (Swift 브리지 경유).
 * - 두 플랫폼 다 `openid` scope 를 요청해 id_token 을 받는다 (OIDC 활성화 전제).
 *
 * Composable 안에서 [rememberKakaoLoginClient] 로 얻어 코루틴에서 [login] 호출.
 */
expect class KakaoLoginClient {
    suspend fun login(): KakaoLoginResult
}

/** Compose 트리에서 플랫폼 컨텍스트 (LocalContext / LocalUIViewController) 를 주입해 [KakaoLoginClient] 생성. */
@Composable
expect fun rememberKakaoLoginClient(): KakaoLoginClient
