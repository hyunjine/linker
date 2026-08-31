import Foundation
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import Shared

/// Firebase Cloud Messaging 셋업 + APNs 권한 요청 + 토큰 등록 브리지.
///
/// - `iOSApp.init` 에서 `configure()` 호출 → FirebaseApp.configure() + delegate 세팅.
/// - 로그인 후 (또는 앱 진입 후) `requestPermissionAndToken()` 호출 → 알림 권한 요청 · APNs 등록.
/// - APNs 이 device token 을 주면 Firebase 가 자동으로 FCM 토큰과 매핑 → `messaging(_:didReceiveRegistrationToken:)`
///   델리게이트로 shared 의 `FcmTokenBridge.onTokenRefreshedAsync(token, "ios")` 호출.
final class LinkerPushBridge: NSObject, UNUserNotificationCenterDelegate, MessagingDelegate {

    static let shared = LinkerPushBridge()

    func configure() {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
    }

    /// 알림 표시 권한 요청 후 APNs 등록. 사용자가 거부하면 조용히 no-op.
    func requestPermissionAndToken() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("[FCM] 알림 권한 요청 실패: \(error)")
                return
            }
            guard granted else {
                print("[FCM] 알림 권한 거부됨")
                return
            }
            DispatchQueue.main.async {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    // MARK: - MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else {
            print("[FCM] token nil")
            return
        }
        print("[FCM] token: \(token.prefix(12))…")
        FcmTokenBridge.shared.onTokenRefreshedAsync(token: token, platform: "ios")
    }

    // MARK: - UNUserNotificationCenterDelegate

    /// Foreground 상태에서 도착한 노티도 배너 표시.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void,
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }
}
