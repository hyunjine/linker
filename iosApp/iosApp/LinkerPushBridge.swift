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

    /// MessagingDelegate 에서 받아 캐시. `ensureFcmTokenRegistered()` 가 이걸 우선 사용해
    /// "APNs token 아직" (FCM error 505) 상황을 방어. delegate 는 APNs 등록 완료 후에만 fire.
    private var cachedFcmToken: String?

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

    /// 앱 복귀 · 로그인 성공 등 세션이 있을 만한 시점에 호출.
    /// FCM SDK 캐시된 토큰을 명시적으로 fetch 해 shared 로 upsert 요청.
    ///
    /// 이유: `messaging(_:didReceiveRegistrationToken:)` delegate 는 토큰이 **새로 발급/rotate**
    /// 될 때만 fire. 첫 실행 delegate 시점엔 아직 로그인 전이라 upsert 가 no-op 로 조용히 실패하고,
    /// 이후 로그인해도 캐시된 동일 토큰이라 delegate 는 다시 fire 안 함 → user_devices 에 row 없이
    /// 사용자가 방치되는 케이스가 있었다. 이 메서드가 그 gap 을 메꾼다.
    ///
    /// 세션이 없어도 shared 의 `UserDevicesRepository.upsertMyDevice` 가 auth.uid() 없으면
    /// no-op 이라 안전. 반복 호출도 idempotent.
    ///
    /// 우선순위:
    /// 1) delegate 로 이미 받은 [cachedFcmToken] 사용 — APNs 등록 완료 후에만 fire 되므로 안전
    /// 2) 없으면 `Messaging.messaging().token { ... }` fetch 시도. APNs 아직이면 error 505.
    ///    이 경우 조용히 skip (delegate 가 나중에 fire 되면 자동 upsert 됨).
    func ensureFcmTokenRegistered() {
        if let token = cachedFcmToken {
            print("[FCM] ensure token upsert (cached): \(token.prefix(12))…")
            FcmTokenBridge.shared.onTokenRefreshedAsync(token: token, platform: "ios")
            return
        }
        Messaging.messaging().token { token, error in
            if let error = error {
                // 초기 실행 시 APNs 등록 완료 전 호출되면 여기 옴 — delegate 가 곧 fire 될 것.
                print("[FCM] token fetch 스킵 (아직 준비 안 됨): \(error.localizedDescription)")
                return
            }
            guard let token = token else { return }
            print("[FCM] ensure token upsert (fresh): \(token.prefix(12))…")
            self.cachedFcmToken = token
            FcmTokenBridge.shared.onTokenRefreshedAsync(token: token, platform: "ios")
        }
    }

    // MARK: - MessagingDelegate

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else {
            print("[FCM] token nil")
            return
        }
        print("[FCM] token: \(token.prefix(12))…")
        cachedFcmToken = token
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
