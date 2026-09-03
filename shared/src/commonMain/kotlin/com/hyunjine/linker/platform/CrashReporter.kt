package com.hyunjine.linker.platform

/**
 * 원격 크래시/예외 리포터. Firebase Crashlytics 로 non-fatal 예외를 남겨 배포 빌드에서 발생한
 * 로그인 실패 같은 원인을 추적한다.
 *
 * - Android: FirebaseCrashlytics.getInstance().recordException / log
 * - iOS: Swift 브리지 (CrashReporterBridge) 경유 Crashlytics.crashlytics().record(error:)
 *
 * TestFlight/Release 빌드에서 콘솔 로그를 볼 수 없을 때 원격 스택트레이스를 남기기 위한 용도.
 * 사용자 취소처럼 정상 흐름은 기록하지 않는다.
 */
expect object CrashReporter {
    /** 브레드크럼 로그 — 다음 recordException 리포트에 함께 첨부된다. */
    fun log(message: String)

    /**
     * Non-fatal 예외 기록. Crashlytics 콘솔 → Non-fatal issues 에 노출된다.
     * [message] 는 스택트레이스 앞에 브레드크럼으로 붙는다.
     */
    fun recordException(throwable: Throwable, message: String? = null)
}
