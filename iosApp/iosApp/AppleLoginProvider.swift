import AuthenticationServices
import CryptoKit
import Foundation
import UIKit
import Shared

/// Apple Sign-In 을 실행하고 결과를 shared 로 넘겨주는 Swift 브리지.
///
/// - `iOSApp.init` 에서 `AppleLoginBridge.shared.handler = { AppleLoginProvider.shared.signIn { ... } }` 로 연결.
/// - `signIn(completion:)` 은 랜덤 nonce 생성 → SHA256 해싱 → `ASAuthorizationController` 실행 →
///   결과를 `KakaoLoginResult` 계열의 sealed subclass (`AppleLoginResultSuccess` 등) 로 매핑해 콜백.
/// - Supabase 검증을 위해 **raw nonce** 도 함께 반환한다 (Supabase 가 SHA256(raw) 를 계산해서 id_token 의 nonce claim 과 비교).
final class AppleLoginProvider: NSObject {

    static let shared = AppleLoginProvider()

    // 현재 진행 중인 요청의 raw nonce · completion 을 유지 (delegate 콜백 시점에 참조).
    private var currentNonce: String?
    private var currentCompletion: ((AppleLoginResult) -> Void)?
    // ASAuthorizationController 는 delegate/presentationContextProvider 를 weak 로 잡기 때문에
    // provider 자체가 shared singleton 으로 유지되어야 안전.

    func signIn(completion: @escaping (AppleLoginResult) -> Void) {
        let raw = Self.randomNonceString()
        currentNonce = raw
        currentCompletion = completion

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(raw)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    // MARK: - Helpers

    /// 32자 랜덤 nonce. 대소문자 + 숫자 + 몇몇 기호. Apple 문서 예제 그대로.
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

    private func finish(_ result: AppleLoginResult) {
        let completion = currentCompletion
        currentCompletion = nil
        currentNonce = nil
        completion?(result)
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension AppleLoginProvider: ASAuthorizationControllerDelegate {

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization,
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            finish(AppleLoginResultFailure(reason: "unexpected credential type: \(type(of: authorization.credential))"))
            return
        }
        guard let identityTokenData = credential.identityToken,
              let identityToken = String(data: identityTokenData, encoding: .utf8),
              !identityToken.isEmpty else {
            finish(AppleLoginResultFailure(reason: "identityToken missing/empty"))
            return
        }
        guard let raw = currentNonce else {
            finish(AppleLoginResultFailure(reason: "nonce state lost — 중복 요청?"))
            return
        }
        print("[AppleLogin] ok — idToken=\(identityToken.prefix(24))… userId=\(credential.user)")
        finish(AppleLoginResultSuccess(idToken: identityToken, rawNonce: raw))
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error,
    ) {
        let nsError = error as NSError
        // ASAuthorizationError.Code.canceled = 1001
        if nsError.domain == ASAuthorizationError.errorDomain,
           nsError.code == ASAuthorizationError.canceled.rawValue {
            print("[AppleLogin] cancelled")
            finish(AppleLoginResultCancelled.shared)
        } else {
            print("[AppleLogin] error: \(error)")
            finish(AppleLoginResultFailure(reason: "\(error)"))
        }
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension AppleLoginProvider: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // 활성 UIWindowScene 의 keyWindow 를 anchor 로 사용. 없으면 UIWindow() 임시.
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return scene?.windows.first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}
