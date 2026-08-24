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
        KakaoSDK.initSDK(appKey: appKey)

        // Kotlin/Native ↔ Swift 브리지 세팅. shared 의 KakaoLoginClient 가 이 handler 를 호출한다.
        // 톡 설치 시 톡 로그인, 없으면 카카오 계정 웹 로그인 폴백.
        KakaoLoginBridge.shared.handler = { callback in
            let onComplete: (OAuthToken?, Error?) -> Void = { token, error in
                let result: KakaoLoginResult
                if let token = token {
                    print("[KakaoLogin] ===== OAuthToken =====")
                    print("[KakaoLogin] accessToken: \(token.accessToken)")
                    print("[KakaoLogin] refreshToken: \(token.refreshToken ?? "nil")")
                    print("[KakaoLogin] scopes: \(String(describing: token.scopes))")
                    print("[KakaoLogin] expiredAt: \(String(describing: token.expiredAt))")

                    // 유저 정보도 조회해서 로그 (개발 확인용).
                    UserApi.shared.me { user, meErr in
                        if let user = user {
                            print("[KakaoLogin] ===== User =====")
                            print("[KakaoLogin] id: \(String(describing: user.id))")
                            print("[KakaoLogin] nickname: \(user.kakaoAccount?.profile?.nickname ?? "nil")")
                            print("[KakaoLogin] profileImageUrl: \(user.kakaoAccount?.profile?.profileImageUrl?.absoluteString ?? "nil")")
                            print("[KakaoLogin] email: \(user.kakaoAccount?.email ?? "nil")")
                        } else if let meErr = meErr {
                            print("[KakaoLogin] me() failed: \(meErr)")
                        }
                    }

                    result = KakaoLoginResultSuccess(accessToken: token.accessToken, refreshToken: token.refreshToken)
                } else if let error = error {
                    // 취소는 SdkError.ClientFailed(reason:.Cancelled) — SDK 버전마다 코드가 달라
                    // 문자열 매칭으로 안전하게 처리.
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
