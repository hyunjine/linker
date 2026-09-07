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

    // MARK: - Silent Push (#194)

    /// 백그라운드 silent push 수신 진입점. AppDelegate 의
    /// `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)` 가 이걸 호출.
    /// payload data 에 `reason = widget_refresh` 있으면 WidgetSync 트리거.
    ///
    /// iOS 는 이 콜백에 최대 ~30초 실행 시간을 주고 completionHandler 호출 여부·
    /// 결과값으로 앱의 백그라운드 성실도를 평가. 실패 계속되면 이후 silent push
    /// throttle 되므로 반드시 completionHandler 호출.
    func handleRemoteNotification(
        _ userInfo: [AnyHashable: Any],
        completionHandler: @escaping (UIBackgroundFetchResult) -> Void,
    ) {
        let reason = userInfo["reason"] as? String
        guard reason == "widget_refresh" else {
            // 다른 종류의 push (스케줄 알림 등) 는 UNUserNotification 이 처리.
            completionHandler(.noData)
            return
        }
        print("[FCM] silent push received reason=widget_refresh")
        WidgetSync.refresh { success in
            completionHandler(success ? .newData : .failed)
        }
    }
}
