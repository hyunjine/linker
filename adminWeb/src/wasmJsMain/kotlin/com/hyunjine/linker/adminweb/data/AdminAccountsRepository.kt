package com.hyunjine.linker.adminweb.data

import com.hyunjine.linker.adminweb.data.remote.AdminSupabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

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
     * `users` × `user_devices` inner join 으로 푸시 가능한 계정 목록을 조회한다.
     *
     * Postgrest embed 문법:
     *  - `user_devices!inner(platform)` — device 가 하나 이상 존재하는 유저만 반환.
     *  - `!inner` 를 빼면 device 없는 유저가 `user_devices = []` 로 함께 나와 잡음.
     *
     * 정렬은 서버 사이드 nickname 오름차순 (§4.1). NULL 닉네임은 도메인 매핑 단계에서
     * 빈 문자열로 접어 UI 가 non-null [String] 로 취급할 수 있게 한다.
     *
     * @return 닉네임 오름차순으로 정렬된 [Account] 리스트. device 는 최소 하나 존재.
     */
    suspend fun fetchAll(): List<Account> {
        val rows: List<AdminUserRowRaw> = AdminSupabase.client
            .from("users")
            .select(
                columns = Columns.raw("id, nickname, avatar_kind, user_devices!inner(platform)"),
            ) {
                order(column = "nickname", order = Order.ASCENDING)
            }
            .decodeList<AdminUserRowRaw>()

        return rows.map { row ->
            Account(
                id = row.id,
                nickname = row.nickname.orEmpty(),
                avatarKind = row.avatarKind,
                platforms = row.userDevices.map { Platform.of(it.platform) }.toSet(),
            )
        }
    }
}
