import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        print("[Auth] iOSApp init: process start")
        // Supabase 클라이언트 lazy 초기화 트리거. 링킹 · Secrets 주입 조기 검증.
        print("[Supabase] project = \(SupabaseProvider.shared.warmUp())")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Chrome/Safari 또는 ASWebAuthenticationSession 에서 OAuth 콜백으로 돌아온 URL.
                    // shared 의 handleAuthDeeplinks 가 supabase-kt 로 위임해 세션에 반영.
                    print("[Auth] onOpenURL: \(url.absoluteString)")
                    AuthDeeplinksIosKt.handleAuthDeeplinks(url: url)
                }
                .onAppear {
                    print("[Auth] ContentView onAppear")
                }
        }
    }
}
