package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable

/**
 * 카카오 SDK 로그인 결과. 성공 시 [Success.accessToken] 을 서버로 전달해 세션 교환한다.
 * 상세는 `docs/api-design.md` §3.1 `POST /auth/kakao`.
 */
sealed interface KakaoLoginResult {
    data class Success(
        val accessToken: String,
        /** SDK 설정에 따라 없을 수도 있음. 서버 세션 발급 후엔 안 씀. */
        val refreshToken: String? = null,
    ) : KakaoLoginResult

    /** 사용자 취소 (뒤로가기 등). 로그인 화면에서 조용히 무시. */
    data object Cancelled : KakaoLoginResult

    /** SDK/네트워크/카카오 서버 오류. [reason] 은 개발자 로그용. UI 에는 표준 문구. */
    data class Failure(val reason: String) : KakaoLoginResult
}

/**
 * 카카오 SDK 로그인 실행자. 플랫폼별 actual 이 실제 SDK 호출을 담당.
 *
 * - Android: 카카오톡 앱이 설치돼 있으면 톡 로그인, 없으면 카카오 계정 웹 로그인 폴백.
 * - iOS: 후속 이슈. 현재는 [KakaoLoginResult.Failure] 반환 (SDK 미연동).
 *
 * Composable 안에서 [rememberKakaoLoginClient] 로 얻어 코루틴에서 [login] 호출.
 */
expect class KakaoLoginClient {
    suspend fun login(): KakaoLoginResult
}

/** Compose 트리에서 플랫폼 컨텍스트를 주입해 [KakaoLoginClient] 를 만든다. */
@Composable
expect fun rememberKakaoLoginClient(): KakaoLoginClient
