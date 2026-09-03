package com.hyunjine.linker.platform

/**
 * iOS Kotlin/Native ↔ Swift 브리지. FirebaseCrashlytics 는 Swift 로 배포되어 Kotlin/Native 에서 직접
 * 참조가 어려워, Swift 쪽에서 [Crashlytics] 호출을 수행하고 shared 는 이 브리지로만 위임한다.
 *
 * 사용 (iOSApp.swift):
 * ```swift
 * CrashReporterBridge.shared.recordHandler = { message, name, reason in
 *     let userInfo: [String: Any] = [ NSLocalizedDescriptionKey: reason ?? "unknown", ... ]
 *     Crashlytics.crashlytics().record(error: NSError(domain: name, code: 0, userInfo: userInfo))
 * }
 * CrashReporterBridge.shared.logHandler = { message in
 *     Crashlytics.crashlytics().log(message)
 * }
 * ```
 *
 * handler 미세팅 시 recordException 은 조용히 무시 (Swift 초기화 전 호출 방어).
 */
object CrashReporterBridge {

    /**
     * Swift 에서 세팅. shared 가 non-fatal 예외를 기록하고 싶을 때 호출한다.
     * - [message]: 브레드크럼 메시지 (nullable)
     * - [exceptionName]: Throwable 의 클래스 심플명 (`error:` 의 NSError.domain 으로 사용)
     * - [reason]: Throwable.message (사용자에게는 노출하지 않는 개발자용 사유)
     */
    var recordHandler: ((message: String?, exceptionName: String, reason: String?) -> Unit)? = null

    /** Swift 에서 세팅. 다음 record 리포트에 첨부될 브레드크럼 로그. */
    var logHandler: ((message: String) -> Unit)? = null
}
