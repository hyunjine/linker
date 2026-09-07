import UIKit

/// SwiftUI `@main` App 은 기본적으로 UIApplicationDelegate 콜백에 접근 못 함.
/// silent push (`application(_:didReceiveRemoteNotification:fetchCompletionHandler:)`)
/// 처리에 이 콜백이 필요해 얇은 AppDelegate 를 두고 `@UIApplicationDelegateAdaptor`
/// 로 iOSApp 에서 연결한다.
///
/// Firebase 세팅 · UNUserNotificationCenter delegate 는 iOSApp.init 에서
/// `LinkerPushBridge.shared.configure()` 로 이미 세팅됨. 여기서는 순수 forwarding 만.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        // 실제 초기화는 iOSApp.init 이 담당. 여기선 flag 만 true 반환.
        return true
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
