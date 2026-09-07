import SwiftUI
import Shared
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser
import GoogleSignIn
import BackgroundTasks

@main
struct iOSApp: App {
    // 홈화면 · 잠금화면 위젯이 볼 오늘 일정 payload 를 앱이 write.
    // scenePhase 변화 감지에 필요.
    @Environment(\.scenePhase) private var scenePhase

    // BGAppRefreshTask identifier. Info.plist BGTaskSchedulerPermittedIdentifiers 와 일치.
    private static let widgetRefreshTaskId = "com.hyunjine.linker.widget-refresh"

    init() {
        // Debug 빌드에서만 테스트용 email/password 로그인 UI 를 노출하기 위한 플래그.
        // Release 빌드에는 이 블록이 컴파일되지 않아 enabled=false 유지.
        #if DEBUG
        DebugConfig.shared.enabled = true
        #endif

        // BGAppRefreshTask 핸들러 등록. UIApplication 이 완전히 뜨기 전에 등록되어야
        // 시스템이 백그라운드 실행 시 우리 코드를 부를 수 있음.
        // 자정 근방에 iOS 가 앱을 잠깐 (~30초) 깨워 WidgetSync.refresh() 실행 (#191).
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.widgetRefreshTaskId,
            using: nil,
        ) { task in
            handleWidgetRefreshTask(task as! BGAppRefreshTask)
        }

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

        // 카카오 SDK 초기화. 네이티브 앱 키는 Config.xcconfig → Info.plist (KAKAO_NATIVE_APP_KEY).
        let appKey = Bundle.main.object(forInfoDictionaryKey: "KAKAO_NATIVE_APP_KEY") as? String ?? ""
        print("[KakaoLogin] init — appKey='\(appKey)' length=\(appKey.count)")
        KakaoSDK.initSDK(appKey: appKey)

        // Supabase 클라이언트 lazy 초기화 트리거. 링킹 · Secrets 주입 조기 검증.
        print("[Supabase] project = \(SupabaseProvider.shared.warmUp())")

        // Kotlin/Native ↔ Swift 브리지 세팅. shared 의 KakaoLoginClient 가 이 handler 를 호출.
        // OIDC 활성화는 카카오 콘솔 · 앱 설정 · 카카오 로그인 · OpenID Connect 활성화 ON 으로
        // 결정됨 (SDK 파라미터로 요청하는 게 아님). 활성화되면 token.idToken 자동 포함.
        // 톡 설치 시 톡 로그인, 없으면 카카오 계정 웹 로그인 폴백.
        KakaoLoginBridge.shared.handler = { callback in
            let onComplete: (OAuthToken?, Error?) -> Void = { token, error in
                let result: KakaoLoginResult
                if let token = token {
                    print("[KakaoLogin] ===== OAuthToken =====")
                    print("[KakaoLogin] accessToken: \(token.accessToken.prefix(12))…")
                    print("[KakaoLogin] idToken   : \(token.idToken?.prefix(24) ?? "nil")…")
                    print("[KakaoLogin] scopes    : \(String(describing: token.scopes))")

                    if let idToken = token.idToken, !idToken.isEmpty {
                        result = KakaoLoginResultSuccess(idToken: idToken)
                    } else {
                        result = KakaoLoginResultFailure(
                            reason: "id_token 없음 — 콘솔에서 OpenID Connect 활성화 확인",
                        )
                    }
                } else if let error = error {
                    // 취소는 SdkError.ClientFailed(reason:.Cancelled) — SDK 버전마다 코드 달라서 문자열 매칭.
                    let msg = "\(error)"
                    if msg.contains("Cancelled") || msg.contains("cancelled") {
                        result = KakaoLoginResultCancelled.shared
                    } else {
                        result = KakaoLoginResultFailure(reason: msg)
                    }
                } else {
                    result = KakaoLoginResultFailure(reason: "no token, no error")
                }
                callback(result)
            }

            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk { token, error in
                    if let error = error {
                        print("[KakaoLogin] talk login failed, falling back to account: \(error)")
                        UserApi.shared.loginWithKakaoAccount(completion: onComplete)
                    } else {
                        onComplete(token, nil)
                    }
                }
            } else {
                UserApi.shared.loginWithKakaoAccount(completion: onComplete)
            }
        }

        // shared → 위젯 refresh 브리지. 스케줄 CRUD 성공 후 CreateScheduleViewModel /
        // MainViewModel.toggleTaskDone 이 이 handler 를 호출 → 앱이 foreground 에 있어도 즉시 반영.
        WidgetBridge.shared.handler = { WidgetSync.refresh() }

        // Kakao SDK 세션 폐기. Supabase signOut 만으로는 부족 — 안 하면 다음 로그인 시 계정
        // 선택 없이 자동 재로그인됨. 에러가 나도 done() 은 반드시 호출.
        KakaoLoginBridge.shared.logoutHandler = { done in
            UserApi.shared.logout { error in
                if let error = error {
                    print("[KakaoLogin] logout error: \(error)")
                } else {
                    print("[KakaoLogin] logout ok")
                }
                done()
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // 카카오톡에서 로그인 완료 후 우리 앱으로 돌아오는 콜백 URL 처리.
                    if AuthApi.isKakaoTalkLoginUrl(url) {
                        _ = AuthController.handleOpenUrl(url: url)
                        return
                    }
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
            switch phase {
            case .active:
                // foreground 복귀 · 로그인 후 등에도 최신 오늘 일정 반영.
                WidgetSync.refresh()
            case .background:
                // 앱이 백그라운드 진입할 때 다음 자정 refresh 를 예약 (앱을 안 켜도 위젯이 갱신되도록).
                scheduleNextWidgetRefresh()
            default:
                break
            }
        }
    }
}

// MARK: - Background widget refresh

/// 다음 KST 00:05 근방에 BGAppRefreshTask 를 예약. iOS 는 실제 실행 시각을 시스템 부하 ·
/// 배터리 상태 등을 보고 스스로 결정하지만, 우리가 요청한 earliestBeginDate 이후로만 실행.
/// 자정 직후 5분 여유를 두어 시각 오차 · timezone 전환 상황 방어.
private func scheduleNextWidgetRefresh() {
    let request = BGAppRefreshTaskRequest(identifier: "com.hyunjine.linker.widget-refresh")
    request.earliestBeginDate = nextMidnightPlusFiveMinutes()
    do {
        try BGTaskScheduler.shared.submit(request)
        print("[BGWidgetRefresh] scheduled — earliestBeginDate=\(String(describing: request.earliestBeginDate))")
    } catch {
        print("[BGWidgetRefresh] schedule failed: \(error)")
    }
}

/// BGAppRefreshTask 실행 콜백. 시스템이 준 최대 실행 시간 (~30초) 안에 위젯 payload 갱신하고
/// 다음 자정 실행도 다시 예약. expirationHandler 는 시간 초과 시 정리용.
private func handleWidgetRefreshTask(_ task: BGAppRefreshTask) {
    // 다음 자정용 새 요청 즉시 예약 (이번 실행이 실패해도 내일은 시도).
    scheduleNextWidgetRefresh()

    task.expirationHandler = {
        print("[BGWidgetRefresh] expired before completion")
        task.setTaskCompleted(success: false)
    }

    WidgetSync.refresh { success in
        print("[BGWidgetRefresh] refresh completed success=\(success)")
        task.setTaskCompleted(success: success)
    }
}

private func nextMidnightPlusFiveMinutes() -> Date {
    let cal = Calendar.current
    let midnight = cal.nextDate(
        after: Date(),
        matching: DateComponents(hour: 0, minute: 0, second: 0),
        matchingPolicy: .nextTime,
    ) ?? Date().addingTimeInterval(60 * 60 * 6)
    return midnight.addingTimeInterval(5 * 60)
}
