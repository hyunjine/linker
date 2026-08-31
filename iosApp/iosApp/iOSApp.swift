import SwiftUI
import Shared
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser

@main
struct iOSApp: App {
    // 홈화면 · 잠금화면 위젯이 볼 오늘 일정 payload 를 앱이 write.
    // scenePhase 변화 감지에 필요.
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Debug 빌드에서만 테스트용 email/password 로그인 UI 를 노출하기 위한 플래그.
        // Release 빌드에는 이 블록이 컴파일되지 않아 enabled=false 유지.
        #if DEBUG
        DebugConfig.shared.enabled = true
        #endif

        // FCM: FirebaseApp.configure + Messaging/UNUserNotificationCenter delegate 세팅.
        // 실제 알림 권한 요청은 아래 onAppear 에서 (앱 UI 뜬 뒤에 물어보는 게 UX 상 자연스러움).
        LinkerPushBridge.shared.configure()

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
                    }
                }
                .onAppear {
                    // 첫 진입 시 위젯 payload 갱신 (세션 없으면 shared 가 빈 items 로 반환).
                    WidgetSync.refresh()
                    // 알림 권한 요청 (사용자가 수락하면 APNs 등록 → Firebase 가 FCM 토큰 발급 → delegate).
                    LinkerPushBridge.shared.requestPermissionAndToken()
                }
        }
        .onChange(of: scenePhase) { _, phase in
            // foreground 복귀 · 로그인 후 등에도 최신 오늘 일정 반영.
            if phase == .active { WidgetSync.refresh() }
        }
    }
}
