package com.hyunjine.linker.adminweb.data.remote

import com.hyunjine.linker.adminweb.auth.BrowserStorageSessionManager
import com.hyunjine.linker.adminweb.config.AdminWebConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/**
 * `:adminWeb` 전용 Supabase 진입점.
 *
 * `:shared` 의 [`SupabaseProvider`](../../../../../../../shared/src/commonMain/kotlin/com/hyunjine/linker/data/remote/SupabaseProvider.kt)
 * 와 별개로 하나 더 두는 이유:
 * - `:shared` 는 iOS/Android expect/actual 구조라 wasmJs 로 컴파일되지 않음.
 * - adminWeb 는 세션 저장소 스위칭이 필요해 커스텀 [BrowserStorageSessionManager] 를 심어야 함.
 *
 * 설치 모듈:
 * - [Auth] — email/password 로그인 · 세션 관찰 · signOut.
 * - [Postgrest] — 관리자 콘솔 계정 리스트 조회 (#279 이후에 사용).
 * - [Functions] — send-announcement Edge Function 호출 (#279 이후에 사용).
 */
object AdminSupabase {

    /**
     * 세션 저장소 스위칭용 공용 인스턴스. 로그인 UI 컨트롤러가 `switchTo` 로 이동시킨다.
     * 부트 시 기본은 `LOCAL` — supabase-kt 가 `autoLoadFromStorage` 로 세션을 자동 복원할 때
     * 저장소를 자동 탐지 (localStorage → sessionStorage 순) 하므로, 이 초깃값은 최초 저장 방향
     * 결정용일 뿐 이후 자동 로그인 상태에 영향을 주지 않는다.
     */
    val sessionManager: BrowserStorageSessionManager = BrowserStorageSessionManager()

    val client: SupabaseClient by lazy {
        require(AdminWebConfig.isConfigured) {
            "AdminWeb Supabase 시크릿이 비어 있습니다. local.properties 의 " +
                "`adminweb.supabase.url` · `adminweb.supabase.anonKey` 를 채우거나, " +
                "배포 환경 변수 `ADMIN_WEB_SUPABASE_URL` · `ADMIN_WEB_SUPABASE_ANON_KEY` 를 주입하세요."
        }
        createSupabaseClient(
            supabaseUrl = AdminWebConfig.supabaseUrl,
            supabaseKey = AdminWebConfig.supabaseAnonKey,
        ) {
            install(Auth) {
                // 브라우저 저장소를 직접 다루는 커스텀 매니저. #278 §6.2 스토리지 스위칭의 기반.
                sessionManager = this@AdminSupabase.sessionManager
                // supabase-kt 가 알아서 access_token 을 refresh_token 으로 갱신하도록 유지.
                // 갱신 실패 시 sessionStatus 가 RefreshFailure → 로그인 화면으로 리라우트.
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest)
            install(Functions)
        }
    }

    /**
     * lazy 초기화를 부트 시점에 강제 트리거. 시크릿 누락을 조기 감지하고,
     * `autoLoadFromStorage` 가 세션 복원을 시작하도록 한다.
     */
    fun warmUp() {
        client
    }
}
