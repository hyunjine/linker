package com.hyunjine.linker.data.remote

import com.hyunjine.linker.data.Secrets
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase 클라이언트 진입점. 앱 프로세스에 하나만 있으면 되고, 첫 접근 시 지연 초기화.
 *
 * URL · publishable key 는 local.properties → shared/build.gradle.kts 의 generateSecrets 태스크가
 * 만든 [Secrets] 를 참조. 값이 비어있으면 로그인/CRUD 호출 시 supabase-kt 가 예외를 던져 조기에
 * 감지된다 (별도 검증 로직 두지 않음).
 *
 * 현재 설치 모듈:
 * - [Auth]: Supabase Auth (카카오 provider 는 대시보드 활성화만 완료. `signInWith(Kakao)` 는 후속 이슈).
 * - [Postgrest]: PostgREST 자동 API. `from("schedules").select { ... }` 형태로 사용.
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
