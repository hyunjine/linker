package com.hyunjine.linker.data.remote

import com.hyunjine.linker.auth.OutlookAuthClient
import com.hyunjine.linker.data.local.OutlookLocal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
 *
 * 대상 (#242): 공유받은 팀 캘린더 [TeamCalendarName] 에서 `재택(이름) HH:MM-HH:MM` 제목
 * ([RemoteWorkSubject]) 중 괄호 안 이름이 [OutlookLocal.teamName] 인 것만 미러링한다. 팀원 재택 ·
 * 사무실 · 출장 · 휴가 등은 파트너에게 보이지 않는다. 이름이 미설정이면 동기화하지 않는다. 미러는 제목 `재택`, 시각은 제목 안의 시각, 날짜는 시작일 하루.
 * 앱에서 미러를 고치면 [pushEdit] · [pushDelete] 로 원본에도 반영 (양방향).
 */
class OutlookSyncService(
    private val auth: OutlookAuthClient,
    private val graph: OutlookGraphClient = OutlookGraphClient(auth),
) {

    /** 팀 캘린더 id 캐시 — 이름으로 매번 찾지 않도록. */
    private var teamCalendarId: String? = null

    /** 팀 캘린더 id. 내 캘린더 목록에 없으면 예외 (공유가 안 됐거나 이름이 바뀜). */
    private suspend fun teamCalendarId(): String =
        teamCalendarId ?: (graph.findCalendarId(TeamCalendarName).getOrThrow()
            ?: error("$TeamCalendarName 캘린더를 찾지 못함"))
            .also { teamCalendarId = it }

    /**
     * [from] ~ [to] 범위를 Outlook 팀 캘린더와 sync. 미로그인 · 미연동 · 이름 미설정이면 no-op (null).
     * Graph · DB 오류는 예외로 던진다 (호출자가 안내 문구 결정).
     *
     * 앱 foreground sync 와 silent push 백그라운드 sync ([OutlookBackgroundSync]) 가 겹치지 않도록
     * 프로세스 전역 [syncMutex] 로 직렬화한다 (sync 자체는 idempotent 지만 upsert · stale 삭제가 교차하면 낭비).
     *
     * @param from 동기화 시작일 (포함).
     * @param to 동기화 종료일 (포함).
     * @return 성공 시 (upserted, deleted) 카운트, no-op 이면 null
     */
    suspend fun syncRange(from: LocalDate, to: LocalDate): SyncResult? = syncMutex.withLock { syncRangeLocked(from, to) }

    private suspend fun syncRangeLocked(from: LocalDate, to: LocalDate): SyncResult? {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return null
        val coupleId = CouplesRepository.myCoupleIdOrNull() ?: return null
        val account = auth.currentAccount() ?: return null // 연동 안 됨
        val myName = OutlookLocal.teamName ?: return null // 이름 미설정

        val startIso = "${from}T00:00:00"
        val endIso = "${to}T23:59:59"
        val events = graph.calendarView(teamCalendarId(), startIso, endIso).getOrThrow()

        val payloads = events.mapNotNull { it.toMirrorPayload(coupleId, uid, myName) }
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

    /**
     * 앱에서 고친 재택 미러를 원본 Outlook 일정에 반영. 제목은 원본 형식 그대로 시각만 바꾸고
     * ([RemoteWorkSubject.withTimes]), 날짜가 바뀌었으면 원본 시작 · 종료를 같은 일수만큼
     * 옮긴다 (종일 · 시각 등 원본 형태 유지). 본문 · 장소 · 참석자는 건드리지 않는다.
     *
     * @param externalId 미러의 external_id (Graph event id).
     * @param previousDate 수정 전 미러 날짜.
     * @param newDate 수정 후 미러 날짜.
     * @param startTime 수정 후 시작 `HH:mm`. null 이면 원본 제목의 시각 유지.
     * @param endTime 수정 후 종료 `HH:mm`. null 이면 원본 제목의 시각 유지.
     */
    suspend fun pushEdit(
        externalId: String,
        previousDate: LocalDate,
        newDate: LocalDate,
        startTime: String?,
        endTime: String?,
    ): Result<Unit> = runCatching {
        val calendarId = teamCalendarId()
        val original = graph.getEvent(calendarId, externalId).getOrThrow()
        val subject = original.subject?.let { orig ->
            RemoteWorkSubject.parse(orig)?.let { work ->
                RemoteWorkSubject.withTimes(orig, startTime ?: work.startTime, endTime ?: work.endTime)
            }
        }
        val shift = previousDate.daysUntil(newDate)
        val patch = OutlookEventPatch(
            subject = subject?.takeIf { it != original.subject },
            start = if (shift != 0) original.start.shiftDays(shift) else null,
            end = if (shift != 0) original.end.shiftDays(shift) else null,
        )
        if (patch.subject == null && patch.start == null) return@runCatching
        graph.patchEvent(calendarId, externalId, patch).getOrThrow()
    }

    /**
     * 앱에서 지운 재택 미러의 원본 Outlook 일정을 삭제.
     *
     * @param externalId 미러의 external_id (Graph event id).
     */
    suspend fun pushDelete(externalId: String): Result<Unit> =
        runCatching { graph.deleteEvent(teamCalendarId(), externalId).getOrThrow() }

    companion object {
        /** 재택 일정이 올라오는 공유 팀 캘린더 이름. */
        const val TeamCalendarName = "TeamAIVN"

        /** 모든 [OutlookSyncService] 인스턴스가 공유하는 sync 직렬화 락 (#420). */
        private val syncMutex = Mutex()

        /** sync 가 진행 중인지. 백그라운드 sync 가 이미 돌고 있는 sync 를 기다리지 않고 건너뛸 때 사용. */
        val isSyncing: Boolean get() = syncMutex.isLocked

        /**
         * 기본 동기화 범위 — 오늘 기준 약 2달 전 (61일) ~ 3달 뒤 (93일). 앱 foreground sync ·
         * silent push 백그라운드 sync 가 같은 범위를 쓰도록 한 곳에 둔다 (#420).
         *
         * @param today 기준일. 기본값은 기기 시간대의 오늘.
         * @return (from, to) — 둘 다 포함.
         */
        @OptIn(ExperimentalTime::class)
        fun defaultRange(
            today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        ): Pair<LocalDate, LocalDate> =
            today.plus(-61, DateTimeUnit.DAY) to today.plus(93, DateTimeUnit.DAY)

        /**
         * 원본 반영 ([pushEdit] · [pushDelete]) 전용 앱 전역 scope. 편집 화면은 저장 직후 닫혀
         * `rememberCoroutineScope` 가 취소되므로 (ForgottenCoroutineScopeException) 화면 수명과 분리한다.
         */
        val writeBackScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    data class SyncResult(val account: com.hyunjine.linker.auth.OutlookAccount, val upserted: Int, val deleted: Int)

    // ────────── mapping helpers ──────────

    @Serializable
    private data class IdExternal(
        val id: String,
        @SerialName("external_id") val externalId: String,
    )

    // 기본값을 두지 않는다 — Supabase 직렬화는 encodeDefaults=false 라 기본값 필드를 빼고 보내
    // type NOT NULL 위반 · source 가 'internal' 로 저장되는 문제가 생긴다 (#242).
    @Serializable
    private data class OutlookMirrorInsert(
        @SerialName("couple_id") val coupleId: String,
        @SerialName("created_by") val createdBy: String,
        val type: String,
        @SerialName("owner_kind") val ownerKind: String,
        val title: String,
        @SerialName("start_date") val startDate: String,
        @SerialName("end_date") val endDate: String,
        @SerialName("all_day") val allDay: Boolean,
        @SerialName("start_time") val startTime: String?,
        @SerialName("end_time") val endTime: String?,
        @SerialName("is_private") val isPrivate: Boolean,
        val source: String,
        @SerialName("external_id") val externalId: String,
    )

    /** 내 이름의 재택 제목만 미러로 바꾼다. 그 외 일정은 null (미러링 안 함). */
    private fun OutlookEventDto.toMirrorPayload(coupleId: String, uid: String, myName: String): OutlookMirrorInsert? {
        val work = RemoteWorkSubject.parse(subject) ?: return null
        if (!RemoteWorkSubject.sameName(work.name, myName)) return null
        val (startDate, _) = parseGraphDateTime(start.dateTime) ?: return null
        return OutlookMirrorInsert(
            coupleId = coupleId,
            createdBy = uid,
            type = "schedule",
            ownerKind = "me",
            title = RemoteWorkSubject.MirrorTitle,
            startDate = startDate,
            endDate = startDate,
            allDay = false,
            startTime = "${work.startTime}:00",
            endTime = "${work.endTime}:00",
            isPrivate = false,
            source = "outlook",
            externalId = id,
        )
    }

    /** `2026-09-10T09:00:00.0000000` 의 날짜만 [days] 일 옮긴다. 시각 · 타임존은 그대로. */
    private fun OutlookDateTime.shiftDays(days: Int): OutlookDateTime {
        val date = LocalDate.parse(dateTime.substringBefore('T')).plus(days, DateTimeUnit.DAY)
        return copy(dateTime = "$date" + "T" + dateTime.substringAfter('T'))
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
