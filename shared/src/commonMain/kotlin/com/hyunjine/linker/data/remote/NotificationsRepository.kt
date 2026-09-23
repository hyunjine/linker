package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `public.notifications` 조회 (#303 알림 내역). 쓰기는 푸시를 보내는 edge function 만 하고,
 * 앱은 RLS `notifications_select_own` 으로 본인 row 만 읽는다.
 */
object NotificationsRepository {

    /** 한 번에 가져오는 최대 개수. 30일치가 이보다 많으면 오래된 쪽은 생략. */
    private const val MAX_ROWS = 300L

    /**
     * 서버 row.
     *
     * @param kind `partner` · `reminder` · `announcement` · `update`.
     * @param createdAt ISO-8601 timestamptz (예: `2026-09-23T01:37:33.655+00:00`).
     */
    @Serializable
    data class Row(
        val id: String,
        val kind: String,
        val title: String,
        val body: String,
        @SerialName("created_at") val createdAt: String,
    )

    /**
     * [since] 이후 받은 내 알림을 최신순으로. 로그인 전이면 빈 리스트.
     *
     * @param since 조회 시작 시각 (보관 기간 30일 경계).
     */
    suspend fun listSince(since: Instant): List<Row> {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return emptyList()
        return SupabaseProvider.client.from("notifications")
            .select {
                filter {
                    eq("user_id", uid)
                    gte("created_at", since.toString())
                }
                order("created_at", Order.DESCENDING)
                limit(MAX_ROWS)
            }
            .decodeList<Row>()
    }
}
