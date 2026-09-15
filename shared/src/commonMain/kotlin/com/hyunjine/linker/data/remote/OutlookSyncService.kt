package com.hyunjine.linker.data.remote

import com.hyunjine.linker.auth.OutlookAuthClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Outlook → Supabase `schedules` 미러 동기화 서비스.
 *
 * 저장 안 함이 원래 요구였지만 파트너 노출을 위해 mirror 방식으로 결정 (#242). 사용자가 Outlook
 * 연동한 뒤 이 서비스로 date range 단위 sync 를 트리거하면 아래 흐름:
 *  1. Graph API 로 range 안의 이벤트 fetch (반복 시리즈는 발생별 확장)
 *  2. Supabase `schedules` 에 upsert (source='outlook', external_id=graph event.id)
 *  3. range 안에서 서버엔 있는데 방금 fetch 목록엔 없는 rows 는 Outlook 에서 삭제된 것으로 간주,
 *     Supabase 에서도 삭제 → 파트너 화면에서도 자연 소거
 *
 * 반복 시리즈 · exception 은 Graph 가 `calendarView` 에서 발생별로 이미 확장해서 주기 때문에
 * 우리 앱의 `series_id` · `schedule_repeat_rules` 는 사용하지 않는다. Outlook 이벤트는 항상
 * 단일 row 로 mirror (사용자가 앱에서 편집하면 그때 규칙 재해석).
 */
class OutlookSyncService(
    private val auth: OutlookAuthClient,
    private val graph: OutlookGraphClient = OutlookGraphClient(auth),
) {

    /**
     * [from] ~ [to] 범위를 Outlook 과 sync. 연동 안 됐거나 토큰 만료면 no-op 반환 (실패 무시 —
     * 캘린더 rendering 은 계속 진행돼야 함).
     * @return 성공 시 (upserted, deleted) 카운트, 미로그인/오류면 null
     */
    suspend fun syncRange(from: LocalDate, to: LocalDate): SyncResult? {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return null
        val coupleId = CouplesRepository.myCoupleIdOrNull() ?: return null
        val account = auth.currentAccount() ?: return null // 연동 안 됨

        val startIso = "${from}T00:00:00"
        val endIso = "${to}T23:59:59"
        val events = graph.calendarView(startIso, endIso).getOrElse { return null }

        val payloads = events.mapNotNull { it.toMirrorPayload(coupleId, uid) }
        val fetchedIds = payloads.map { it.externalId }.toSet()

        // Upsert. Postgrest 는 conflict target 을 서버 unique 제약 (partial index) 으로 지정 가능.
        if (payloads.isNotEmpty()) {
            SupabaseProvider.client.from("schedules").upsert(payloads) {
                onConflict = "created_by,external_id"
            }
        }

        // 이 range 안에서 서버엔 있는데 fetched 목록엔 없는 outlook mirror rows 삭제.
        val staleIds: List<String> = SupabaseProvider.client.from("schedules")
            .select(columns = Columns.list("id", "external_id")) {
                filter {
                    eq("created_by", uid)
                    eq("source", "outlook")
                    lte("start_date", to.toString())
                    gte("end_date", from.toString())
                }
            }
            .decodeList<IdExternal>()
            .filter { it.externalId !in fetchedIds }
            .map { it.id }

        if (staleIds.isNotEmpty()) {
            SupabaseProvider.client.from("schedules").delete {
                filter { isIn("id", staleIds) }
            }
        }

        return SyncResult(
            account = account,
            upserted = payloads.size,
            deleted = staleIds.size,
        )
    }

    /**
     * Outlook 연동 해제. MSAL 로컬 계정 · refresh_token 삭제 후 사용자의 outlook mirror rows 도
     * 전부 삭제. 파트너 화면에서도 자동 소거.
     */
    suspend fun disconnect() {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
        auth.signOut()
        if (uid != null) {
            SupabaseProvider.client.from("schedules").delete {
                filter {
                    eq("created_by", uid)
                    eq("source", "outlook")
                }
            }
        }
    }

    data class SyncResult(val account: com.hyunjine.linker.auth.OutlookAccount, val upserted: Int, val deleted: Int)

    // ────────── mapping helpers ──────────

    @Serializable
    private data class IdExternal(
        val id: String,
        @SerialName("external_id") val externalId: String,
    )

    @Serializable
    private data class OutlookMirrorInsert(
        @SerialName("couple_id") val coupleId: String,
        @SerialName("created_by") val createdBy: String,
        val type: String = "schedule",
        @SerialName("owner_kind") val ownerKind: String = "me",
        val title: String,
        @SerialName("start_date") val startDate: String,
        @SerialName("end_date") val endDate: String,
        @SerialName("all_day") val allDay: Boolean,
        @SerialName("start_time") val startTime: String? = null,
        @SerialName("end_time") val endTime: String? = null,
        @SerialName("is_private") val isPrivate: Boolean = false,
        val source: String = "outlook",
        @SerialName("external_id") val externalId: String,
    )

    private fun OutlookEventDto.toMirrorPayload(coupleId: String, uid: String): OutlookMirrorInsert? {
        val title = subject?.takeIf { it.isNotBlank() } ?: "(제목 없음)"
        val (startDate, startTime) = parseGraphDateTime(start.dateTime) ?: return null
        val (endDate, endTime) = parseGraphDateTime(end.dateTime) ?: return null
        return OutlookMirrorInsert(
            coupleId = coupleId,
            createdBy = uid,
            title = title,
            startDate = startDate,
            endDate = endDate,
            allDay = isAllDay,
            startTime = if (isAllDay) null else startTime,
            endTime = if (isAllDay) null else endTime,
            externalId = id,
        )
    }

    /**
     * Graph 의 `2026-09-10T09:00:00.0000000` 형식을 (yyyy-MM-dd, HH:mm:ss) 로 분리.
     * `Prefer: outlook.timezone` 헤더로 서버가 지정 TZ 기준 로컬 문자열을 이미 반환하므로
     * 별도 TZ 변환 없이 잘라 쓰기만 하면 된다.
     */
    private fun parseGraphDateTime(raw: String): Pair<String, String>? {
        // 예: "2026-09-10T09:00:00.0000000" — 소수점 이하 잘라내고 T 로 split.
        val trimmed = raw.substringBefore('.')
        val parts = trimmed.split('T')
        if (parts.size != 2) return null
        val date = parts[0]
        // Supabase time 컬럼은 HH:mm:ss 형식 기대. 초 미포함이면 :00 붙임.
        val time = parts[1].let { if (it.length == 5) "$it:00" else it }
        return date to time
    }
}
