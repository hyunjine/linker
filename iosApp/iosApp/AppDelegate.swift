import UIKit
import FirebaseMessaging

/// SwiftUI `@main` App 은 기본적으로 UIApplicationDelegate 콜백에 접근 못 함.
/// APNs 등록 결과 · silent push 등을 받으려면 이 delegate 가 필요해 얇은 AppDelegate
/// 를 두고 `@UIApplicationDelegateAdaptor` 로 iOSApp 에서 연결한다.
///
/// Firebase 자동 swizzling (FirebaseAppDelegateProxyEnabled) 은 Info.plist 에서
/// NO 로 꺼둠 — SwiftUI 의 delegate attach 타이밍과 안 맞아 APNs token 을 놓치기
/// 때문. 여기서 `didRegisterForRemoteNotifications...` 로 APNs token 을 직접
/// `Messaging.messaging().apnsToken` 에 넘겨야 FCM 토큰이 발급된다.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        // 실제 초기화는 iOSApp.init 이 담당. 여기선 flag 만 true 반환.
        return true
    }

    /// APNs 등록 성공. Firebase swizzling 을 꺼놨으니 우리가 직접 Messaging 에 토큰을 전달.
    /// 이걸 해야 FCM SDK 가 APNs↔FCM 토큰 매핑을 하고 FCM 토큰이 발급됨.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data,
    ) {
        Messaging.messaging().apnsToken = deviceToken
        print("[FCM] APNs token 등록 완료 (\(deviceToken.count) bytes)")
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error,
    ) {
        print("[FCM] APNs 등록 실패: \(error)")
    }

    /// Silent push 수신. #194 에서 매일 KST 자정에 서버가 발송해 위젯 payload 를 갱신.
    /// APNs 요구 조건 (background push): apns-push-type=background, priority=5,
    /// aps.content-available=1, notification 없음.
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void,
    ) {
        LinkerPushBridge.shared.handleRemoteNotification(userInfo, completionHandler: completionHandler)
    }
}
