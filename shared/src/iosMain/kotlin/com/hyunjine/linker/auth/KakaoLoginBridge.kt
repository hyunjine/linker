package com.hyunjine.linker.auth

/**
 * iOS Kotlin/Native ↔ Swift 브리지. Kakao iOS SDK 는 Swift 로 배포되어 Kotlin/Native 에서 직접
 * 참조가 어려우므로, Swift 쪽에서 실제 로그인 로직을 실행하고 결과만 이 브리지로 넘긴다.
 *
 * 사용 (iOSApp.swift):
 * ```swift
 * KakaoLoginBridge.shared.handler = { callback in
 *     if UserApi.isKakaoTalkLoginAvailable() {
 *         UserApi.shared.loginWithKakaoTalk { token, error in
 *             callback(KakaoLoginBridge.map(token: token, error: error))
 *         }
 *     } else {
 *         UserApi.shared.loginWithKakaoAccount { ... }
 *     }
 * }
 * ```
 *
 * `handler` 미세팅 상태에서 로그인 시도 시 [KakaoLoginResult.Failure] 반환.
 */
object KakaoLoginBridge {
    /**
     * Swift 에서 세팅. 호출되면 SDK 로그인을 실행하고 결과를 [callback] 으로 돌려준다.
     * Kotlin 은 [callback] 을 통해 [KakaoLoginResult] 를 받아 suspend 재개.
     */
    var handler: ((callback: (KakaoLoginResult) -> Unit) -> Unit)? = null
}
