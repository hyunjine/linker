package com.hyunjine.linker.adminweb.config

/**
 * 빌드 시점에 baked-in 된 시크릿을 런타임 관점에서 소비하기 좋게 감싸는 얇은 어댑터.
 *
 * `AdminWebSecrets` 는 Gradle `generateAdminSecrets` 태스크가 생성하는 상수 오브젝트.
 * 이 파일은 그 원시 문자열을 파싱해 `adminUids: Set<String>` 등 실제 사용 형태로 노출한다.
 */
internal object AdminWebConfig {
    /** Supabase Project URL — `https://<ref>.supabase.co`. */
    val supabaseUrl: String get() = AdminWebSecrets.SupabaseUrl

    /** Supabase publishable(anon) key. RLS 로 실제 접근 통제. */
    val supabaseAnonKey: String get() = AdminWebSecrets.SupabaseAnonKey

    /**
     * 관리자 UID 화이트리스트.
     *
     * `AdminWebSecrets.AdminUidsRaw` 의 콤마 구분 문자열을 파싱해 공백 · 빈 값은 제거.
     * 빈 집합이면 어떤 유저도 관리자 자격을 갖지 못한다.
     */
    val adminUids: Set<String> by lazy {
        AdminWebSecrets.AdminUidsRaw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * 시크릿이 모두 채워졌는지 검사. `false` 라면 로컬 개발 환경에서 `local.properties`
     * 를 채우지 않은 상태이거나 CI 환경 변수가 누락된 것.
     */
    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
