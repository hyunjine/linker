package com.hyunjine.linker.auth

/**
 * iOS Kotlin/Native ↔ Swift 브리지. `GIDSignIn` 은 UIViewController presenter 를 요구하므로
 * Swift 쪽에서 실제 흐름을 실행하고, 결과만 이 브리지로 넘긴다.
 *
 * 사용 (iOSApp.swift):
 * ```swift
 * GoogleLoginBridge.shared.handler = { callback in
 *     GoogleLoginProvider.shared.signIn { result in
 *         callback(result)  // GoogleLoginResult{Success|Cancelled|Failure}
 *     }
 * }
 * ```
 *
 * handler 미세팅 상태에서 로그인 시도 시 [GoogleLoginResult.Failure] 반환.
 */
object GoogleLoginBridge {
    var handler: ((callback: (GoogleLoginResult) -> Unit) -> Unit)? = null
}
