package com.hyunjine.linker.auth

/**
 * iOS Kotlin/Native ↔ Swift 브리지. MSAL 은 `UIViewController` presenter 와 keychain 접근이
 * 필요해 Swift 쪽에서 실제 SDK 호출을 수행하고, Kotlin 은 여기 handler 를 통해 결과만 받는다.
 *
 * Google/AppleLoginBridge 와 같은 패턴이지만 메서드가 4개 (login/signOut/currentAccount/
 * accessToken) 라 각 handler 를 별도 var 로 둔다. handler 는 iOSApp.swift 에서 앱 부트스트랩
 * 시점에 한 번 세팅.
 *
 * ```swift
 * OutlookAuthBridge.shared.loginHandler = { cb in
 *     OutlookAuthProvider.shared.signIn { result in cb(result) }
 * }
 * OutlookAuthBridge.shared.signOutHandler = { cb in
 *     OutlookAuthProvider.shared.signOut { cb() }
 * }
 * OutlookAuthBridge.shared.currentAccountHandler = { cb in
 *     OutlookAuthProvider.shared.currentAccount { account in cb(account) }
 * }
 * OutlookAuthBridge.shared.accessTokenHandler = { cb in
 *     OutlookAuthProvider.shared.accessToken { token in cb(token) }
 * }
 * ```
 *
 * handler 가 세팅되지 않은 상태에서 호출되면 Failure/null 반환 (SPM 미통합 초기 상태 안전 fallback).
 */
object OutlookAuthBridge {
    var loginHandler: ((callback: (OutlookAuthResult) -> Unit) -> Unit)? = null
    var signOutHandler: ((callback: () -> Unit) -> Unit)? = null
    var currentAccountHandler: ((callback: (OutlookAccount?) -> Unit) -> Unit)? = null
    var accessTokenHandler: ((callback: (String?) -> Unit) -> Unit)? = null
}
