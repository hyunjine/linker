package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS MSAL 통합 stub. 실제 SDK 호출은 Swift bridge (`OutlookAuthBridge.swift`) 가 담당하도록
 * 설계돼 있으나, MSAL iOS 는 SPM 패키지 `microsoft-authentication-library-for-objc` 를
 * Xcode UI 로 수동 추가해야 하기 때문에 그 작업 전까지는 아래처럼 Failure 만 반환.
 *
 * **다음 스텝 (사용자 작업):**
 * 1. Xcode → `iosApp` project → PROJECT `iosApp` → Package Dependencies → `+`
 *    URL: `https://github.com/AzureAD/microsoft-authentication-library-for-objc.git`
 *    Rules: Up to Next Major Version from `1.5.0`
 *    Add to Target: `iosApp` (LinkerWidget 는 제외)
 * 2. `iosApp/iosApp/Info.plist` 에 아래 URL scheme 추가 (LSApplicationQueriesSchemes · CFBundleURLTypes):
 *    - Query schemes: `msauthv2`, `msauthv3`
 *    - URL scheme: `msauth.com.hyunjine.linker` (bundle id prefix + msauth.)
 * 3. `iosApp/iosApp/iOSApp.swift` (또는 SceneDelegate) 의 URL open 콜백에서
 *    `MSALPublicClientApplication.handleMSALResponse(url, sourceApplication:)` 호출
 * 4. `OutlookAuthBridge.swift` 를 `iosApp/iosApp/` 에 추가 (별도 커밋에서 제공 예정)
 * 5. 이 파일의 stub 을 `OutlookAuthBridge` 호출로 교체
 */
actual class OutlookAuthClient {
    actual suspend fun login(): OutlookAuthResult =
        OutlookAuthResult.Failure("iOS MSAL 미통합 — SPM 추가 및 Swift bridge 대기")

    actual suspend fun signOut() {
        // no-op until bridge ready
    }

    actual suspend fun currentAccount(): OutlookAccount? = null

    actual suspend fun accessToken(): String? = null
}

@Composable
actual fun rememberOutlookAuthClient(): OutlookAuthClient =
    remember { OutlookAuthClient() }
