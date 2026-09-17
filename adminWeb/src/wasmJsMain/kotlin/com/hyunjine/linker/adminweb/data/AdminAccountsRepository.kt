package com.hyunjine.linker.adminweb.data

import com.hyunjine.linker.adminweb.data.remote.AdminSupabase
import io.github.jan.supabase.postgrest.postgrest

/**
 * 관리자 콘솔의 계정 리스트 데이터 접근 계층.
 * — docs/261-admin-push-console.md §3.3, §4.1.
 *
 * 현재 스코프는 "푸시 가능한 계정 전체를 한 번에 가져오는" [fetchAll] 하나뿐. 검색 · 필터는
 * 클라이언트에서 처리 (계정 수가 소규모이므로 서버 왕복을 줄이는 편이 UX 상 이득).
 * 후속 이슈에서 서버 사이드 검색이 필요해지면 여기에 메서드가 추가된다.
 */
object AdminAccountsRepository {

    /**
     * 관리자 화이트리스트 통과 유저에게만 열려있는 `admin_list_accounts()` Postgres RPC 를 호출한다.
     *
     * 왜 `from("users").select(...)` 대신 RPC 인가:
     * `public.users` · `public.user_devices` 는 각각 self · 커플 스코프 RLS 로 보호돼 있어
     * anon key 세션으로는 관리자 본인 이외의 유저 · device 를 조회할 수 없다. RPC 는
     * `SECURITY DEFINER` 로 RLS 를 우회하되 함수 안에서 `public.is_admin()` 을 재검증하기
     * 때문에 anon key + 세션 JWT 만으로도 안전하게 전체 리스트를 받을 수 있다.
     *
     * 서버 사이드 정렬 (nickname ASC nulls last) 은 함수 안에서 처리한다. NULL 닉네임은
     * 도메인 매핑 단계에서 빈 문자열로 접어 UI 가 non-null [String] 로 취급할 수 있게 한다.
     *
     * @return 닉네임 오름차순으로 정렬된 [Account] 리스트. device 가 하나 이상 존재하는 유저만 포함.
     */
    suspend fun fetchAll(): List<Account> {
        val rows: List<AdminUserRowRaw> = AdminSupabase.client
            .postgrest
            .rpc("admin_list_accounts")
            .decodeList<AdminUserRowRaw>()

        return rows.map { row ->
            Account(
                id = row.id,
                nickname = row.nickname.orEmpty(),
                profileImageUrl = row.profileImageUrl,
                platforms = row.userDevices.map { Platform.of(it.platform) }.toSet(),
            )
        }
    }
}
