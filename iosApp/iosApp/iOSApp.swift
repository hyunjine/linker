import SwiftUI
import Shared
import GoogleSignIn

@main
struct iOSApp: App {
    // 홈화면 · 잠금화면 위젯이 볼 오늘 일정 payload 를 앱이 write.
    // scenePhase 변화 감지에 필요.
    @Environment(\.scenePhase) private var scenePhase

    // SwiftUI @main 은 기본적으로 UIApplicationDelegate 콜백을 못 받음.
    // #194 silent push (자정 위젯 refresh) 처리에 didReceiveRemoteNotification 이
    // 필요해 얇은 AppDelegate 를 붙임.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // Debug 빌드에서만 테스트용 email/password 로그인 UI 를 노출하기 위한 플래그.
        // Release 빌드에는 이 블록이 컴파일되지 않아 enabled=false 유지.
        #if DEBUG
        DebugConfig.shared.enabled = true
        #endif

        // FCM: FirebaseApp.configure + Messaging/UNUserNotificationCenter delegate 세팅.
        // 실제 알림 권한 요청은 아래 onAppear 에서 (앱 UI 뜬 뒤에 물어보는 게 UX 상 자연스러움).
        LinkerPushBridge.shared.configure()

        // Apple Sign-In 브리지. shared 의 AppleLoginClient 가 이 handler 를 호출 → AppleLoginProvider 위임.
        AppleLoginBridge.shared.handler = { callback in
            AppleLoginProvider.shared.signIn { result in
                callback(result)
            }
        }

        // GoogleSignIn 설정. GIDClientID 를 Info.plist 에 중복 저장하지 않고 GoogleService-Info.plist
        // 의 CLIENT_ID 를 그대로 재사용 — Firebase 콘솔에서 Google Auth 켜면 이 값이 자동 채워지므로.
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let plist = NSDictionary(contentsOfFile: path),
           let clientID = plist["CLIENT_ID"] as? String {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            print("[GoogleLogin] configured clientID=\(clientID.prefix(24))…")
        } else {
            print("[GoogleLogin] GoogleService-Info.plist 에 CLIENT_ID 없음 — 재다운로드 필요")
        }

        // Google Sign-In 브리지. shared 의 GoogleLoginClient → GoogleLoginProvider (GIDSignIn).
        GoogleLoginBridge.shared.handler = { callback in
            GoogleLoginProvider.shared.signIn { result in
                callback(result)
            }
        }

        // Supabase 클라이언트 lazy 초기화 트리거. 링킹 · Secrets 주입 조기 검증.
        print("[Supabase] project = \(SupabaseProvider.shared.warmUp())")

        // shared → 위젯 refresh 브리지. 스케줄 CRUD 성공 후 CreateScheduleViewModel /
        // MainViewModel.toggleTaskDone 이 이 handler 를 호출 → 앱이 foreground 에 있어도 즉시 반영.
        WidgetBridge.shared.handler = { WidgetSync.refresh() }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Google Sign-In 웹 콜백 (REVERSED_CLIENT_ID 스킴) 처리.
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
                .onAppear {
                    // 첫 진입 시 위젯 payload 갱신 (세션 없으면 shared 가 빈 items 로 반환).
                    WidgetSync.refresh()
                    // 알림 권한 요청 (사용자가 수락하면 APNs 등록 → Firebase 가 FCM 토큰 발급 → delegate).
                    LinkerPushBridge.shared.requestPermissionAndToken()
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                // foreground 복귀 · 로그인 후 등에도 최신 오늘 일정 반영.
                WidgetSync.refresh()
                // FCM 토큰이 아직 user_devices 에 upsert 안 됐을 수 있음 (첫 실행 시 delegate
                // 는 로그인 전 fire, 이후 캐시된 토큰이라 delegate 재fire X). 명시적 fetch 로
                // gap 커버 — 세션 있을 때만 실제로 upsert 됨.
                LinkerPushBridge.shared.ensureFcmTokenRegistered()
            }
        }
    }
}
