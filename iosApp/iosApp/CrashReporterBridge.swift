import Foundation
import FirebaseCore
import FirebaseCrashlytics
import Shared

/// shared (Kotlin/Native) 의 `CrashReporterBridge` handler 를 세팅해 non-fatal 예외를 Crashlytics 로 전송.
///
/// - `iOSApp.init` 에서 `configure()` 호출 (FirebaseApp.configure 이후에).
/// - shared 는 `CrashReporter.recordException(...)` 만 호출하고, 실제 Crashlytics 호출은 여기서 담당.
/// - TestFlight/Release 빌드에서 카카오 로그인 실패 원인을 원격에서 확인하기 위한 용도.
enum CrashReporterInstaller {

    static func configure() {
        CrashReporterBridge.shared.logHandler = { message in
            Crashlytics.crashlytics().log(message)
        }
        CrashReporterBridge.shared.recordHandler = { message, exceptionName, reason in
            if let message = message, !message.isEmpty {
                Crashlytics.crashlytics().log(message)
            }
            let domain = exceptionName.isEmpty ? "KotlinThrowable" : exceptionName
            let userInfo: [String: Any] = [
                NSLocalizedDescriptionKey: reason ?? "unknown",
                "breadcrumb": message ?? "",
            ]
            let error = NSError(domain: domain, code: 0, userInfo: userInfo)
            Crashlytics.crashlytics().record(error: error)
        }
    }

    /// Swift 쪽 (iOSApp.swift KakaoLoginBridge.handler) 에서 잡은 error 를 직접 Crashlytics 로 보내는 헬퍼.
    /// shared 의 KakaoLoginResult.Failure 로 매핑되기 전에 원본 NSError 를 그대로 남겨 원인 파악에 유리.
    static func recordKakaoLoginError(_ error: Error, breadcrumb: String) {
        Crashlytics.crashlytics().log("[Kakao] " + breadcrumb)
        Crashlytics.crashlytics().record(error: error)
    }
}
