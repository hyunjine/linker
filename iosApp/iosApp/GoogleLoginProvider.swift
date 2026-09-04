import CryptoKit
import Foundation
import GoogleSignIn
import UIKit
import Shared

/// Google Sign-In 을 실행하고 결과를 shared 로 넘겨주는 Swift 브리지.
///
/// - `iOSApp.init` 에서 `GoogleLoginBridge.shared.handler = { GoogleLoginProvider.shared.signIn { ... } }` 로 연결.
/// - `GIDSignIn.sharedInstance.signIn(withPresenting:hint:additionalScopes:nonce:)` 를 호출.
///   nonce 는 raw 랜덤 → SHA256 hex 형태로 전달, Supabase 검증용 raw nonce 는 별도로 반환.
/// - `GIDClientID` (iOS OAuth Client ID) 는 Info.plist 에서 자동 로드 (또는 `GIDConfiguration` 명시).
///   Info.plist 에 다음 두 키 필요:
///     - `GIDClientID` = `<IOS_CLIENT_ID>.apps.googleusercontent.com`
///     - `CFBundleURLSchemes` 배열에 REVERSED_CLIENT_ID (`com.googleusercontent.apps.<IOS_CLIENT_ID>`) 추가
/// - `iOSApp.body.onOpenURL` 에서 `GIDSignIn.sharedInstance.handle(url)` 호출 필요.
final class GoogleLoginProvider: NSObject {

    static let shared = GoogleLoginProvider()

    func signIn(completion: @escaping (GoogleLoginResult) -> Void) {
        guard let presenter = Self.topViewController() else {
            completion(GoogleLoginResultFailure(reason: "no presenting view controller"))
            return
        }

        let rawNonce = Self.randomNonceString()
        let hashedNonce = Self.sha256(rawNonce)

        GIDSignIn.sharedInstance.signIn(
            withPresenting: presenter,
            hint: nil,
            additionalScopes: nil,
            nonce: hashedNonce,
        ) { signInResult, error in
            if let error = error {
                let nsError = error as NSError
                // GIDSignInError.canceled = -5
                if nsError.domain == kGIDSignInErrorDomain, nsError.code == GIDSignInError.canceled.rawValue {
                    print("[GoogleLogin] cancelled")
                    completion(GoogleLoginResultCancelled.shared)
                } else {
                    print("[GoogleLogin] error: \(error)")
                    completion(GoogleLoginResultFailure(reason: "\(error)"))
                }
                return
            }
            guard let user = signInResult?.user,
                  let idToken = user.idToken?.tokenString,
                  !idToken.isEmpty else {
                completion(GoogleLoginResultFailure(reason: "idToken missing/empty"))
                return
            }
            print("[GoogleLogin] ok — idToken=\(idToken.prefix(24))… email=\(user.profile?.email ?? "nil")")
            completion(GoogleLoginResultSuccess(idToken: idToken, rawNonce: rawNonce))
        }
    }

    // MARK: - Helpers

    /// 활성 UIWindowScene 의 root view controller 를 재귀 탐색해 topmost 를 반환. presenter 인자로 사용.
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    /// 32자 랜덤 nonce (Apple provider 와 동일한 charset · 로직).
    private static func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] =
            Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        while remainingLength > 0 {
            let randoms: [UInt8] = (0 ..< 16).map { _ in
                var random: UInt8 = 0
                let errorCode = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                assert(errorCode == errSecSuccess, "SecRandomCopyBytes failed: \(errorCode)")
                return random
            }
            randoms.forEach { random in
                if remainingLength == 0 { return }
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        let hashed = SHA256.hash(data: Data(input.utf8))
        return hashed.compactMap { String(format: "%02x", $0) }.joined()
    }
}
