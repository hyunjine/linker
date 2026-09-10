import Foundation
import MSAL
import Shared
import UIKit

/// MSAL 을 실행하고 결과를 shared 로 넘겨주는 Swift 브리지.
///
/// - `iOSApp.init` 에서 4개 handler 를 세팅해 shared 의 `OutlookAuthClient` 가 호출 가능.
/// - `MSALPublicClientApplication` 인스턴스는 최초 접근 시 lazy 생성 후 재사용 (SDK 권장).
/// - refresh_token 은 iOS Keychain 에 저장돼 앱 재시작 후에도 자동 로그인 유지.
///
/// Info.plist 요구사항 (별도 커밋으로 이미 반영):
///   - `CFBundleURLTypes` → scheme `msauth.com.hyunjine.linker`
///   - `LSApplicationQueriesSchemes` → `msauthv2`, `msauthv3`
///
/// iOSApp `body.onOpenURL` 에서 `MSALPublicClientApplication.handleMSALResponse(url, sourceApplication:)` 호출 필수.
final class OutlookAuthProvider: NSObject {

    static let shared = OutlookAuthProvider()

    /// Azure Portal 앱 등록의 Application (client) ID. Config.xcconfig 의 `OUTLOOK_CLIENT_ID`
    /// 와 동일해야 함 (build-time secret pipeline 이 Kotlin 쪽에 baked-in 하는 값).
    private let clientId = "4978b49b-6810-4e3e-9ee8-bea4631667f9"

    /// Multi-tenant + personal accounts 를 위한 authority. `common` 이 두 유형 모두 허용.
    private let authorityUrl = "https://login.microsoftonline.com/common"

    /// Redirect URI. Azure 에 등록된 것과 정확히 일치. MSAL iOS 는 기본 형식이 `msauth.<bundleId>://auth`.
    private let redirectUri = "msauth.com.hyunjine.linker://auth"

    /// Graph API delegated scopes. Azure API permissions 에 부여된 것 중 실제 요청할 subset.
    /// offline_access 는 authority 설정으로 자동 부여 (명시적 request 불필요).
    private let scopes = ["Calendars.ReadWrite", "User.Read"]

    /// SDK 인스턴스 lazy 캐시. 실패 시 nil — 이 경우 모든 요청은 조기 실패 처리.
    private lazy var application: MSALPublicClientApplication? = {
        do {
            let authority = try MSALAADAuthority(url: URL(string: authorityUrl)!)
            let config = MSALPublicClientApplicationConfig(
                clientId: clientId,
                redirectUri: redirectUri,
                authority: authority
            )
            return try MSALPublicClientApplication(configuration: config)
        } catch {
            print("[OutlookAuth] MSAL 초기화 실패: \(error)")
            return nil
        }
    }()

    // MARK: - Public API (called from iOSApp bridge handlers)

    /// 대화형 로그인. 저장된 계정이 있으면 silent 로 우선 시도 → 실패 시 대화형 fallback.
    func signIn(completion: @escaping (OutlookAuthResult) -> Void) {
        guard let application = application else {
            completion(OutlookAuthResultFailure(reason: "MSAL init 실패"))
            return
        }
        guard let presenter = Self.topViewController() else {
            completion(OutlookAuthResultFailure(reason: "no presenting view controller"))
            return
        }
        // 기존 계정 있으면 silent 시도 → 계정 선택 시트 스킵.
        readCurrentAccount(application: application) { [weak self] existing in
            guard let self = self else { return }
            if let existing = existing {
                self.acquireSilent(application: application, account: existing) { silentResult in
                    if let silentResult = silentResult {
                        completion(silentResult)
                    } else {
                        self.acquireInteractive(application: application, presenter: presenter, completion: completion)
                    }
                }
            } else {
                self.acquireInteractive(application: application, presenter: presenter, completion: completion)
            }
        }
    }

    func signOut(completion: @escaping () -> Void) {
        guard let application = application else { completion(); return }
        readCurrentAccount(application: application) { account in
            guard let account = account else { completion(); return }
            do {
                let params = MSALSignoutParameters()
                params.signoutFromBrowser = false
                application.signout(with: account, signoutParameters: params) { _, _ in
                    completion()
                }
            }
        }
    }

    /// 저장된 계정 정보 반환 (실제 토큰 발급 없이 로컬 캐시만 조회). 없으면 nil.
    func currentAccount(completion: @escaping (OutlookAccount?) -> Void) {
        guard let application = application else { completion(nil); return }
        readCurrentAccount(application: application) { account in
            completion(account.map { $0.toKotlin() })
        }
    }

    /// fresh access_token 발급. 만료 시 refresh_token 으로 자동 갱신 (SDK 내부).
    /// 실패 (계정 없음 · refresh 만료) 시 nil → 호출자는 재로그인 유도.
    func accessToken(completion: @escaping (String?) -> Void) {
        guard let application = application else { completion(nil); return }
        readCurrentAccount(application: application) { [weak self] account in
            guard let self = self, let account = account else { completion(nil); return }
            self.acquireSilent(application: application, account: account) { result in
                if case let s as OutlookAuthResultSuccess = result {
                    completion(s.accessToken)
                } else {
                    completion(nil)
                }
            }
        }
    }

    // MARK: - Internals

    private func readCurrentAccount(
        application: MSALPublicClientApplication,
        completion: @escaping (MSALAccount?) -> Void
    ) {
        let parameters = MSALParameters()
        parameters.completionBlockQueue = DispatchQueue.main
        application.getCurrentAccount(with: parameters) { current, _, error in
            if let error = error { print("[OutlookAuth] getCurrentAccount 실패: \(error)") }
            completion(current)
        }
    }

    private func acquireSilent(
        application: MSALPublicClientApplication,
        account: MSALAccount,
        completion: @escaping (OutlookAuthResult?) -> Void
    ) {
        let params = MSALSilentTokenParameters(scopes: scopes, account: account)
        application.acquireTokenSilent(with: params) { result, error in
            if let error = error {
                let nsError = error as NSError
                // MSALError.interactionRequired = -50002 → 대화형 필요 신호
                if nsError.domain == MSALErrorDomain,
                   nsError.code == MSALError.interactionRequired.rawValue {
                    print("[OutlookAuth] silent 실패 (UI 필요)")
                } else {
                    print("[OutlookAuth] silent 실패: \(error)")
                }
                completion(nil)
                return
            }
            guard let result = result else { completion(nil); return }
            completion(result.toKotlin())
        }
    }

    private func acquireInteractive(
        application: MSALPublicClientApplication,
        presenter: UIViewController,
        completion: @escaping (OutlookAuthResult) -> Void
    ) {
        let webParams = MSALWebviewParameters(authPresentationViewController: presenter)
        let params = MSALInteractiveTokenParameters(scopes: scopes, webviewParameters: webParams)
        application.acquireToken(with: params) { result, error in
            if let error = error {
                let nsError = error as NSError
                // MSALError.userCanceled = -50000
                if nsError.domain == MSALErrorDomain,
                   nsError.code == MSALError.userCanceled.rawValue {
                    print("[OutlookAuth] cancelled")
                    completion(OutlookAuthResultCancelled.shared)
                } else {
                    print("[OutlookAuth] interactive 실패: \(error)")
                    completion(OutlookAuthResultFailure(reason: "\(error)"))
                }
                return
            }
            guard let result = result else {
                completion(OutlookAuthResultFailure(reason: "result nil"))
                return
            }
            print("[OutlookAuth] ok — \(result.account.username ?? "unknown")")
            completion(result.toKotlin())
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

// MARK: - MSAL ↔ Kotlin mappers

private extension MSALResult {
    func toKotlin() -> OutlookAuthResult {
        let claims = account.accountClaims ?? [:]
        let email = (claims["preferred_username"] as? String)
            ?? (claims["email"] as? String)
            ?? (account.username ?? "")
        let displayName = claims["name"] as? String
        return OutlookAuthResultSuccess(
            accountId: account.identifier ?? "",
            email: email,
            displayName: displayName,
            accessToken: accessToken
        )
    }
}

private extension MSALAccount {
    func toKotlin() -> OutlookAccount {
        let claims = accountClaims ?? [:]
        let email = (claims["preferred_username"] as? String)
            ?? (claims["email"] as? String)
            ?? (username ?? "")
        let displayName = claims["name"] as? String
        return OutlookAccount(
            accountId: identifier ?? "",
            email: email,
            displayName: displayName
        )
    }
}
