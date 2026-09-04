package com.hyunjine.linker.auth

/**
 * iOS Kotlin/Native ↔ Swift 브리지. `ASAuthorizationController` 관련 API 는 SwiftUI/UIKit
 * presenter · delegate 를 요구하므로 Swift 쪽에서 실제 흐름을 실행하고, 결과만 이 브리지로 넘긴다.
 *
 * 사용 (iOSApp.swift):
 * ```swift
 * AppleLoginBridge.shared.handler = { callback in
 *     AppleLoginProvider.shared.signIn { result in
 *         callback(result)  // AppleLoginResult{Success|Cancelled|Failure}
 *     }
 * }
 * ```
 *
 * handler 미세팅 상태에서 로그인 시도 시 [AppleLoginResult.Failure] 반환.
 */
object AppleLoginBridge {
    /**
     * Swift 에서 세팅. Kotlin 이 login() 호출하면 이 handler 를 통해 Swift 로 위임되고,
     * Swift 는 결과를 [callback] 으로 돌려준다.
     */
    var handler: ((callback: (AppleLoginResult) -> Unit) -> Unit)? = null
}
