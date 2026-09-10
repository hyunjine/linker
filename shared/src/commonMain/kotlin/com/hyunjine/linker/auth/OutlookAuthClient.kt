package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable

/**
 * Outlook (Microsoft Entra ID) 로그인 결과.
 *
 * Google/Apple 과 달리 Supabase 로 넘기지 **않는다** — 앱 자체 계정 인증이 아니라 사용자가
 * 자기 회사 · 개인 Outlook 캘린더 데이터에 접근할 수 있게 권한 위임을 받는 용도. 즉 여기서
 * 얻는 [Success.accessToken] 은 Microsoft Graph API 호출용 Bearer token 이고, 이 토큰의
 * 저장·갱신은 MSAL SDK 가 keychain/EncryptedSharedPreferences 로 알아서 관리한다.
 */
sealed interface OutlookAuthResult {
    /**
     * @param accountId MSAL 이 발급한 안정적인 계정 식별자 (Graph API `/me/id` 와는 다름).
     *   재로그인 시 이 값으로 계정 매칭.
     * @param email 사용자의 primary email (`preferred_username` claim). 드로워에 표시.
     * @param displayName 사용자 이름 (`name` claim). 없을 수도 있음.
     * @param accessToken Graph API 호출용 Bearer 토큰. 만료 시 [OutlookAuthClient.accessToken]
     *   재호출로 자동 갱신 (MSAL SDK 내부에서 refresh_token 사용).
     */
    data class Success(
        val accountId: String,
        val email: String,
        val displayName: String?,
        val accessToken: String,
    ) : OutlookAuthResult

    /** 사용자가 로그인 시트/브라우저를 닫음. 조용히 무시. */
    data object Cancelled : OutlookAuthResult

    /** MSAL SDK · 네트워크 · Azure AD 오류. [reason] 은 개발자 로그용. */
    data class Failure(val reason: String) : OutlookAuthResult
}

/** 저장된 계정 정보 (앱 재시작 후 상태 복원용). [accessToken] 은 필요 시 [OutlookAuthClient.accessToken] 로 갱신. */
data class OutlookAccount(
    val accountId: String,
    val email: String,
    val displayName: String?,
)

/**
 * Outlook 계정 로그인 · 토큰 발급 실행자.
 *
 * - Android: `com.microsoft.identity.client:msal` 의 `SingleAccountPublicClientApplication`.
 * - iOS: SPM 의 `microsoft-authentication-library-for-objc` (MSAL 클래스) 를 Swift 브리지가 호출.
 *
 * 두 플랫폼 모두 refresh_token 을 SDK 내부에서 안전 저장 (Keychain · EncryptedSharedPreferences)
 * 하므로 앱은 refresh 로직을 직접 구현하지 않는다. [accessToken] 호출 시 만료됐으면 SDK 가 자동
 * 갱신 후 새 토큰을 반환한다 (refresh_token 도 만료됐으면 [OutlookAuthResult.Failure] 반환 → 재로그인 UX).
 *
 * 필요한 Graph scope: `Calendars.ReadWrite` · `offline_access` · `User.Read` (Azure AD 앱 등록 시 부여).
 */
expect class OutlookAuthClient {
    /**
     * 대화형 로그인. Microsoft 로그인 시트 (iOS: ASWebAuthenticationSession, Android: Chrome
     * Custom Tab) 를 띄워 계정 선택 + 동의를 받고 access_token 을 발급.
     */
    suspend fun login(): OutlookAuthResult

    /**
     * 앱 저장소에서 계정을 삭제 (SDK 캐시 · refresh_token 포함). 이후 [currentAccount] 는 null.
     * Microsoft 서버 세션 자체는 브라우저에 남아있어 다음 로그인 시 계정 선택 시트가 빠르게 뜬다.
     */
    suspend fun signOut()

    /** 저장된 계정이 있으면 반환. 없으면 null. UI 진입 시 "연동됨" 여부 판단용. */
    suspend fun currentAccount(): OutlookAccount?

    /**
     * 저장된 계정으로 fresh access_token 을 발급. 만료됐으면 refresh_token 으로 자동 갱신.
     * refresh_token 도 만료됐거나 계정이 없으면 null 반환 → 호출자는 재로그인 유도.
     */
    suspend fun accessToken(): String?
}

/** Compose 트리에서 플랫폼 컨텍스트 (Android Activity · iOS UIViewController) 를 주입해 생성. */
@Composable
expect fun rememberOutlookAuthClient(): OutlookAuthClient
