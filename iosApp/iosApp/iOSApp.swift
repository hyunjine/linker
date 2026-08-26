import SwiftUI
import Shared
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser

@main
struct iOSApp: App {
    init() {
        // 카카오 SDK 초기화. 네이티브 앱 키는 Config.xcconfig → Info.plist (KAKAO_NATIVE_APP_KEY).
        let appKey = Bundle.main.object(forInfoDictionaryKey: "KAKAO_NATIVE_APP_KEY") as? String ?? ""
        print("[KakaoLogin] init — appKey='\(appKey)' length=\(appKey.count)")
        KakaoSDK.initSDK(appKey: appKey)

        // Supabase 클라이언트 lazy 초기화 트리거. 링킹 · Secrets 주입 조기 검증.
        print("[Supabase] project = \(SupabaseProvider.shared.warmUp())")

        // Kotlin/Native ↔ Swift 브리지 세팅. shared 의 KakaoLoginClient 가 이 handler 를 호출.
        // openid scope 명시 → OIDC 활성화된 앱에서만 token.idToken 반환됨.
        // 톡 설치 시 톡 로그인, 없으면 카카오 계정 웹 로그인 폴백.
        KakaoLoginBridge.shared.handler = { callback in
            let scopes = ["openid"]
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
                UserApi.shared.loginWithKakaoTalk(scopes: scopes) { token, error in
                    if let error = error {
                        print("[KakaoLogin] talk login failed, falling back to account: \(error)")
                        UserApi.shared.loginWithKakaoAccount(scopes: scopes, completion: onComplete)
                    } else {
                        onComplete(token, nil)
                    }
                }
            } else {
                UserApi.shared.loginWithKakaoAccount(scopes: scopes, completion: onComplete)
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
        }
    }
}
