package com.hyunjine.linker.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS 카카오 로그인. KakaoOpenSDK (CocoaPods/SPM) 세팅은 별도 후속 이슈에서 진행.
 * 현재는 SDK 미연동 상태이므로 항상 [KakaoLoginResult.Failure] 반환.
 *
 * 붙이는 방향:
 *   1) iosApp Podfile 에 `KakaoSDKUser`, `KakaoSDKAuth`, `KakaoSDKCommon` 추가
 *   2) `iOSApp.swift` (or AppDelegate) 에서 `KakaoSDK.initSDK(appKey:)`
 *   3) `SceneDelegate.scene(_:openURLContexts:)` 또는 `App.onOpenURL` 에서 `AuthController.handleOpenUrl`
 *   4) KMP 쪽 actual 을 Objective-C interop 으로 `UserApi.shared.loginWithKakaoTalk` 호출
 */
actual class KakaoLoginClient {
    actual suspend fun login(): KakaoLoginResult =
        KakaoLoginResult.Failure("iOS Kakao SDK not integrated yet")
}

@Composable
actual fun rememberKakaoLoginClient(): KakaoLoginClient =
    remember { KakaoLoginClient() }
