package com.hyunjine.linker.data.remote

import com.hyunjine.linker.data.Secrets
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Supabase 클라이언트 진입점. 앱 프로세스에 하나만 있으면 되고, 첫 접근 시 지연 초기화.
 *
 * URL · publishable key 는 local.properties → shared/build.gradle.kts 의 generateSecrets 태스크가
 * 만든 [Secrets] 를 참조.
 *
 * 현재 설치 모듈:
 * - [Auth]: Supabase Auth. 인증은 카카오 SDK 로그인 → `signInWith(IDToken, Kakao)` 흐름. 별도
 *   OAuth 딥링크 (scheme/host) 는 불필요해 설정하지 않는다.
 * - [Postgrest]: PostgREST 자동 API.
 * - [Realtime]: postgres_changes 구독. 파트너 변경 즉시 sync (schedules · anniversaries · users).
 *
 * Swift 에서는 `SupabaseProvider.shared.client` 로 접근.
 */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = Secrets.SupabaseUrl,
            supabaseKey = Secrets.SupabasePublishableKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    /**
     * 앱 진입점 (Android Application · iOS App) 에서 호출해 lazy 초기화를 강제 트리거.
     * supabase-kt 타입을 밖으로 노출하지 않도록 순수 [String] 만 반환한다.
     */
    fun warmUp(): String {
        client
        return Secrets.SupabaseUrl
    }
}
