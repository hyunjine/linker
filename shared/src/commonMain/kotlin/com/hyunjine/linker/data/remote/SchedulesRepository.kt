package com.hyunjine.linker.data.remote

import com.hyunjine.linker.feature.schedule.RepeatRule
import com.hyunjine.linker.feature.schedule.ScheduleDraft
import com.hyunjine.linker.feature.schedule.ScheduleOwner
import com.hyunjine.linker.feature.schedule.ScheduleType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `public.schedules` CRUD. RLS `schedules_all_in_my_couple` 로 내 커플 스케줄만 접근 가능.
 * couple_id 는 매 호출마다 fresh 조회 (`myCoupleId()`).
 *
 * 반복 규칙 (`schedule_repeat_rules`) 은 각 materialized row 마다 복제 저장 (denormalize).
 * 편집 UI 가 어느 인스턴스에서 열려도 자기 규칙을 그대로 복원할 수 있게 하기 위함.
 * `weekly_days` 는 bitmask (MON=1<<0..SUN=1<<6, kotlinx `DayOfWeek.ordinal` 기준).
 *
 * 시리즈 개념: 같은 반복 규칙으로 생성된 인스턴스들은 `schedules.series_id` 를 공유.
 * 편집 · 삭제는 series_id 로 batch 처리 → 시리즈 전체 일괄 적용.
 */
object SchedulesRepository {

    /** materialize 상한. 사용자가 실수로 "매일 · 종료 100년 뒤" 같은 걸 골라도 안전. */
    private const val MAX_SERIES_INSTANCES = 1000

    /**
     * 서버 row 형태. 컬럼명이 snake_case 라 그대로 프로퍼티 이름으로 매핑
     * (Postgrest 기본 CAMEL_CASE_TO_SNAKE_CASE 변환은 update DSL 에서만 쓰이고, 역방향 디코딩엔
     * `@SerialName` 이 정답). start_time/end_time 은 "HH:MM:SS" 문자열로 옴.
     */
    @Serializable
    data class Row(
        val id: String,
        @SerialName("couple_id") val coupleId: String,
        @SerialName("created_by") val createdBy: String,
        val type: String,        // 'task' | 'schedule'
        @SerialName("owner_kind") val ownerKind: String, // 'me' | 'partner' | 'us'
        val title: String,
        @SerialName("start_date") val startDate: String, // ISO
        @SerialName("end_date") val endDate: String,
        @SerialName("all_day") val allDay: Boolean,
        @SerialName("start_time") val startTime: String? = null,
        @SerialName("end_time") val endTime: String? = null,
        @SerialName("is_done") val isDone: Boolean,
        @SerialName("is_private") val isPrivate: Boolean = false,
        @SerialName("series_id") val seriesId: String? = null,
    )

    /**
     * INSERT 전용 payload. `Row` 를 그대로 쓰면 `id = "00000000-..."` placeholder 가 실제 값으로
     * 서버에 기록되어 (Postgres DEFAULT 는 컬럼 명시 시 발동하지 않음) PK 유니크 위반 · 캘린더에
     * placeholder UUID 저장이 발생. id · is_done · created_at · updated_at 은 서버 DEFAULT 로 채워지므로
     * 여기서 아예 필드를 뺀다. series_id 는 반복 시리즈일 때만 채워진다.
     */
    @Serializable
    data class InsertPayload(
        @SerialName("couple_id") val coupleId: String,
        @SerialName("created_by") val createdBy: String,
        val type: String,
        @SerialName("owner_kind") val ownerKind: String,
        val title: String,
        @SerialName("start_date") val startDate: String,
        @SerialName("end_date") val endDate: String,
        @SerialName("all_day") val allDay: Boolean,
        @SerialName("start_time") val startTime: String? = null,
        @SerialName("end_time") val endTime: String? = null,
        @SerialName("is_private") val isPrivate: Boolean = false,
        @SerialName("series_id") val seriesId: String? = null,
    )

    /** `schedule_repeat_rules` row. 필요한 필드만 nullable — CHECK 제약은 서버가 검증. */
    @Serializable
    data class RepeatRow(
        @SerialName("schedule_id") val scheduleId: String,
        val kind: String, // 'daily' | 'weekly' | 'monthly' | 'yearly'
        @SerialName("weekly_days") val weeklyDays: Short? = null,
        @SerialName("monthly_day") val monthlyDay: Short? = null,
        @SerialName("yearly_month") val yearlyMonth: Short? = null,
        @SerialName("yearly_day") val yearlyDay: Short? = null,
        @SerialName("ends_at") val endsAt: String? = null,
    )

    /**
     * 현재 유저의 couple_id. 없으면 null (아직 커플 미가입).
     * 캐시하지 않는다 — couple 이동 · 파트너 참여 등으로 값이 바뀌면 즉시 반영돼야 하므로
     * 매 호출마다 fresh 조회. couple_members 는 로우 1~2개짜리 tiny 테이블이라 오버헤드 미미.
     */
    suspend fun myCoupleId(): String? = CouplesRepository.myCoupleIdOrNull()

    /**
     * 단일 스케줄 조회 후 UI draft 로 변환. 없거나 조회 실패 시 null.
     * 반복 규칙도 함께 fetch (`schedule_repeat_rules` 별건 조회).
     */
    suspend fun getDraftById(id: String): ScheduleDraft? {
        val row = SupabaseProvider.client.from("schedules")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Row>() ?: return null
        val repeatRow = SupabaseProvider.client.from("schedule_repeat_rules")
            .select { filter { eq("schedule_id", id) } }
            .decodeSingleOrNull<RepeatRow>()
        val rule = repeatRow?.toRule() ?: RepeatRule.None
        val endDate = repeatRow?.endsAt?.let { LocalDate.parse(it) }
        val viewerId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        return row.toDraft(rule, endDate, viewerId)
    }

    /**
     * 제목 부분일치 (대소문자 무시) 검색. 검색어에 `%` `_` 는 이스케이프하지 않아 사용자가 넣으면
     * PostgreSQL wildcard 로 동작 — 초기 스코프에서는 문제로 안 보고 그대로 통과. 빈 문자열은 빈 결과.
     */
    suspend fun search(query: String): List<Row> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val coupleId = myCoupleId() ?: return emptyList()
        return SupabaseProvider.client.from("schedules")
            .select {
                filter {
                    eq("couple_id", coupleId)
                    ilike("title", "%$trimmed%")
                }
                order("start_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<Row>()
    }

    /** `[from, to]` 범위와 겹치는 스케줄 조회. 반씩 겹치는 것도 포함. */
    suspend fun listInRange(from: LocalDate, to: LocalDate): List<Row> {
        val coupleId = myCoupleId() ?: return emptyList()
        return SupabaseProvider.client.from("schedules")
            .select {
                filter {
                    eq("couple_id", coupleId)
                    lte("start_date", to.toString())
                    gte("end_date", from.toString())
                }
            }
            .decodeList<Row>()
    }

    /**
     * 새 스케줄 저장. 반환값은 대표 row 의 id.
     *
     * - 반복 없음: 단일 row insert
     * - 반복 있음: [expandOccurrences] 로 종료일까지의 인스턴스를 계산해서 batch insert.
     *   모든 row 는 새 series_id 를 공유하고 각자 schedule_repeat_rules 복제본을 가짐.
     */
    suspend fun create(draft: ScheduleDraft): String {
        val coupleId = myCoupleId() ?: error("커플에 속하지 않은 유저가 스케줄 저장 시도")
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("세션 없이 스케줄 저장 시도")

        if (draft.repeat == RepeatRule.None) {
            val inserted = SupabaseProvider.client.from("schedules")
                .insert(draft.toInsertPayload(coupleId, uid, seriesId = null)) { select() }
                .decodeSingle<Row>()
            return inserted.id
        }
        return insertSeries(draft, coupleId, uid)
    }

    /**
     * 기존 스케줄 갱신. 반환값은 대표 row 의 id (편집한 인스턴스의 id 를 유지하려 노력).
     *
     * 시나리오별 처리:
     * - 기존/신규 모두 단일: 기존 row 단일 UPDATE (+ 반복 규칙 정리)
     * - 기존 단일 → 신규 시리즈: 기존 row 삭제 + 새 시리즈 생성
     * - 기존 시리즈 → 신규 단일: 시리즈 전체 삭제 + 단일 row 생성
     * - 기존/신규 모두 시리즈이고 규칙 동일: 시리즈 전체 batch UPDATE (title/type/times/owner/allDay 만)
     * - 기존/신규 모두 시리즈이고 규칙 변경: 시리즈 전체 삭제 + 새 시리즈 생성 (is_done 초기화 감수)
     *
     * `owner_kind` 는 DB 에 creator 관점으로 저장. draft.owner 는 viewer 관점이라
     * viewer != creator 인 경우 me ↔ partner 스왑.
     */
    suspend fun update(id: String, draft: ScheduleDraft): String {
        val coupleId = myCoupleId() ?: error("커플에 속하지 않은 유저가 스케줄 편집 시도")
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("세션 없이 스케줄 편집 시도")

        val oldSeriesId = draft.seriesId
        val oldRepeat = oldSeriesId?.let { fetchRepeatRuleForSchedule(id) } ?: RepeatRule.None
        val oldEndDate = oldSeriesId?.let { fetchRepeatEndsAt(id) }
        // 공개 → 비공개 전환은 Supabase Realtime UPDATE 이벤트가 NEW row RLS 로
        // 필터돼 파트너에게 안 감. 파트너 로컬 캐시 stale 방지 위해 DELETE + 새 INSERT
        // 로 재생성해 파트너가 DELETE 이벤트를 받도록 (#161 과 동일 취지).
        val oldRow = SupabaseProvider.client.from("schedules")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Row>()
        val oldIsPrivate = oldRow?.isPrivate ?: false
        val ruleChanged = oldRepeat != draft.repeat || oldEndDate != draft.repeatEndDate
        val privateTransition = !oldIsPrivate && draft.isPrivate

        return when {
            oldSeriesId == null && draft.repeat == RepeatRule.None && !privateTransition -> {
                updateSingleRow(id, draft, uid)
                id
            }
            oldSeriesId == null && draft.repeat == RepeatRule.None && privateTransition -> {
                // 단일 row + 비공개 전환 → DELETE + 새 INSERT
                deleteRowById(id)
                insertSingle(draft, coupleId, uid)
            }
            oldSeriesId == null && draft.repeat != RepeatRule.None -> {
                deleteRowById(id)
                insertSeries(draft, coupleId, uid)
            }
            oldSeriesId != null && draft.repeat == RepeatRule.None -> {
                deleteSeriesById(oldSeriesId)
                insertSingle(draft, coupleId, uid)
            }
            oldSeriesId != null && !ruleChanged && !privateTransition -> {
                updateSeriesMetadata(oldSeriesId, draft, uid)
                id
            }
            else -> {
                // 시리즈 · 규칙 변경 or 비공개 전환 → 전체 재생성
                deleteSeriesById(oldSeriesId!!)
                insertSeries(draft, coupleId, uid)
            }
        }
    }

    /**
     * 뷰어 관점의 owner ("me"/"partner"/"us") 를 creator 관점으로 되돌린다.
     * DB 는 항상 creator 관점으로 저장하므로, 파트너가 만든 걸 내가 편집해서 owner 를 바꿔 저장할 때 스왑 필요.
     * createdBy 를 모르는 경우 (신규 draft — 이 함수는 update 에서만 씀) 는 그대로 둔다.
     */
    private fun ownerKindForStorage(viewerOwner: String, createdBy: String?, viewerId: String?): String {
        if (viewerOwner == "us" || createdBy == null || viewerId == null) return viewerOwner
        val viewerIsCreator = createdBy == viewerId
        return when (viewerOwner) {
            "me" -> if (viewerIsCreator) "me" else "partner"
            "partner" -> if (viewerIsCreator) "partner" else "me"
            else -> viewerOwner
        }
    }

    /**
     * 삭제. 시리즈에 속하면 시리즈 전체 삭제 (사용자의 "일괄 적용" 방침에 맞춤).
     * schedule_repeat_rules 는 FK ON DELETE CASCADE 로 함께 삭제됨.
     */
    suspend fun delete(id: String) {
        val row = SupabaseProvider.client.from("schedules")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Row>()
        val seriesId = row?.seriesId
        if (seriesId != null) deleteSeriesById(seriesId) else deleteRowById(id)
    }

    /** 할 일 (`type='task'`) 의 `is_done` 만 단일 컬럼 갱신. DayDetailSheet 체크박스 토글에서 사용. */
    suspend fun setTaskDone(id: String, done: Boolean) {
        SupabaseProvider.client.from("schedules").update({
            set("is_done", done)
        }) {
            filter { eq("id", id) }
        }
    }

    // ─── Private helpers ────────────────────────────────────────

    /** 반복 시리즈 batch insert. materialize 개수는 [MAX_SERIES_INSTANCES] 로 상한. */
    private suspend fun insertSeries(draft: ScheduleDraft, coupleId: String, uid: String): String {
        val seriesId = randomUuid()
        val occurrences = expandOccurrences(draft)
        require(occurrences.isNotEmpty()) { "반복 규칙이 종료일 안에 생성하는 인스턴스가 없다" }
        val payloads = occurrences.map { (s, e) ->
            draft.toInsertPayload(coupleId, uid, seriesId = seriesId).copy(
                startDate = s.toString(),
                endDate = e.toString(),
            )
        }
        val inserted = SupabaseProvider.client.from("schedules")
            .insert(payloads) { select() }
            .decodeList<Row>()
        // 각 row 에 반복 규칙 복제. ends_at 은 draft.repeatEndDate 재사용.
        val repeatRows = inserted.mapNotNull { row -> draft.repeat.toRow(row.id, draft.repeatEndDate) }
        if (repeatRows.isNotEmpty()) {
            SupabaseProvider.client.from("schedule_repeat_rules").insert(repeatRows)
        }
        return inserted.first().id
    }

    /** 단일 row UPDATE + 반복 규칙 정리. 시리즈가 아닌 케이스에서만 호출. */
    private suspend fun updateSingleRow(id: String, draft: ScheduleDraft, viewerId: String) {
        val ownerForStorage = ownerKindForStorage(draft.owner.toDbValue(), draft.createdBy, viewerId)
        SupabaseProvider.client.from("schedules").update({
            set("type", draft.type.toDbValue())
            set("owner_kind", ownerForStorage)
            set("title", draft.title)
            set("start_date", draft.startDate.toString())
            set("end_date", draft.endDate.toString())
            set("all_day", draft.allDay)
            set("start_time", draft.startTimeForDb())
            set("end_time", draft.endTimeForDb())
            set("is_private", draft.isPrivate)
        }) {
            filter { eq("id", id) }
        }
        applyRepeatRule(id, draft.repeat, draft.repeatEndDate)
    }

    /** 단일 row INSERT. 시리즈에서 단일로 downgrade · 비공개 재생성 등에서 재사용. 반환값: 새 id. */
    private suspend fun insertSingle(draft: ScheduleDraft, coupleId: String, uid: String): String {
        val inserted = SupabaseProvider.client.from("schedules")
            .insert(draft.toInsertPayload(coupleId, uid, seriesId = null)) { select() }
            .decodeSingle<Row>()
        applyRepeatRule(inserted.id, draft.repeat, draft.repeatEndDate)
        return inserted.id
    }

    /**
     * 시리즈 전체 metadata batch UPDATE. 규칙 · 날짜는 그대로 두고 (start_date/end_date 는
     * 인스턴스마다 다르므로) title/type/times/owner/allDay/is_private 만 반영.
     */
    private suspend fun updateSeriesMetadata(seriesId: String, draft: ScheduleDraft, viewerId: String) {
        val ownerForStorage = ownerKindForStorage(draft.owner.toDbValue(), draft.createdBy, viewerId)
        SupabaseProvider.client.from("schedules").update({
            set("type", draft.type.toDbValue())
            set("owner_kind", ownerForStorage)
            set("title", draft.title)
            set("all_day", draft.allDay)
            set("start_time", draft.startTimeForDb())
            set("end_time", draft.endTimeForDb())
            set("is_private", draft.isPrivate)
        }) {
            filter { eq("series_id", seriesId) }
        }
    }

    private suspend fun deleteRowById(id: String) {
        SupabaseProvider.client.from("schedules").delete {
            filter { eq("id", id) }
        }
    }

    /** 시리즈 전체 삭제. schedule_repeat_rules 는 CASCADE. */
    private suspend fun deleteSeriesById(seriesId: String) {
        SupabaseProvider.client.from("schedules").delete {
            filter { eq("series_id", seriesId) }
        }
    }

    private suspend fun fetchRepeatRuleForSchedule(id: String): RepeatRule {
        val row = SupabaseProvider.client.from("schedule_repeat_rules")
            .select { filter { eq("schedule_id", id) } }
            .decodeSingleOrNull<RepeatRow>() ?: return RepeatRule.None
        return row.toRule()
    }

    private suspend fun fetchRepeatEndsAt(id: String): LocalDate? {
        val row = SupabaseProvider.client.from("schedule_repeat_rules")
            .select { filter { eq("schedule_id", id) } }
            .decodeSingleOrNull<RepeatRow>() ?: return null
        return row.endsAt?.let { LocalDate.parse(it) }
    }

    /**
     * `rule` 이 [RepeatRule.None] 이면 기존 규칙 삭제, 그 외에는 upsert.
     * schedule_repeat_rules 는 schedule_id 가 PK 라 upsert 로 idempotent.
     * 시리즈용 batch 처리는 [insertSeries] 에서 별도로 담당.
     */
    private suspend fun applyRepeatRule(scheduleId: String, rule: RepeatRule, endDate: LocalDate?) {
        if (rule == RepeatRule.None) {
            SupabaseProvider.client.from("schedule_repeat_rules").delete {
                filter { eq("schedule_id", scheduleId) }
            }
            return
        }
        val row = rule.toRow(scheduleId, endDate) ?: return
        SupabaseProvider.client.from("schedule_repeat_rules").upsert(row)
    }
}

/**
 * 반복 규칙 + 종료일 → materialized 인스턴스 리스트.
 * 각 인스턴스는 원본과 동일한 duration (endDate - startDate) 을 유지한다.
 * 상한 초과 시 예외 (사용자에게는 "종료일이 너무 멀다" 정도의 메시지가 적절).
 */
internal fun expandOccurrences(draft: ScheduleDraft): List<Pair<LocalDate, LocalDate>> {
    val start = draft.startDate
    val end = draft.endDate
    val until = draft.repeatEndDate
        ?: error("반복 규칙이 지정됐지만 종료일이 없음 — RepeatPickerSheet 이 강제해야 함")
    require(start <= until) { "반복 종료일이 시작일보다 이전" }
    val duration = start.daysUntil(end)  // >= 0 by schedules CHECK
    val result = mutableListOf<Pair<LocalDate, LocalDate>>()

    fun emit(s: LocalDate) {
        result += s to s.plus(duration, DateTimeUnit.DAY)
    }

    when (val rule = draft.repeat) {
        RepeatRule.None -> Unit
        RepeatRule.Daily -> {
            var cursor = start
            while (cursor <= until && result.size < 1000) {
                emit(cursor); cursor = cursor.plus(1, DateTimeUnit.DAY)
            }
        }
        is RepeatRule.Weekly -> {
            if (rule.days.isEmpty()) {
                emit(start)  // 요일 미선택 방어 — 단발성 저장
            } else {
                var cursor = start
                while (cursor <= until && result.size < 1000) {
                    if (cursor.dayOfWeek in rule.days) emit(cursor)
                    cursor = cursor.plus(1, DateTimeUnit.DAY)
                }
            }
        }
        is RepeatRule.Monthly -> {
            var cursor = start
            while (cursor <= until && result.size < 1000) {
                if (cursor.dayOfMonth == rule.day) emit(cursor)
                cursor = cursor.plus(1, DateTimeUnit.DAY)
            }
        }
        is RepeatRule.Yearly -> {
            var cursor = start
            while (cursor <= until && result.size < 1000) {
                if (cursor.monthNumber == rule.month && cursor.dayOfMonth == rule.day) emit(cursor)
                cursor = cursor.plus(1, DateTimeUnit.DAY)
            }
        }
    }
    return result
}

/** draft → INSERT payload. RLS `schedules_all_in_my_couple` 이 `created_by = auth.uid()` 를 요구하므로 명시 포함. */
private fun ScheduleDraft.toInsertPayload(
    coupleId: String,
    createdBy: String,
    seriesId: String?,
) = SchedulesRepository.InsertPayload(
    coupleId = coupleId,
    createdBy = createdBy,
    type = type.toDbValue(),
    ownerKind = owner.toDbValue(),
    title = title,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    allDay = allDay,
    startTime = startTimeForDb(),
    endTime = endTimeForDb(),
    isPrivate = isPrivate,
    seriesId = seriesId,
)

private fun ScheduleType.toDbValue(): String = when (this) {
    ScheduleType.Task -> "task"
    ScheduleType.Schedule -> "schedule"
}

private fun ScheduleOwner.toDbValue(): String = when (this) {
    ScheduleOwner.Me -> "me"
    ScheduleOwner.Partner -> "partner"
    ScheduleOwner.Us -> "us"
}

/** UI 는 "HH:MM" 만 다루지만 Postgres TIME 은 "HH:MM:SS" 를 기대. all-day/task 는 null. */
private fun ScheduleDraft.startTimeForDb(): String? =
    if (showsTimeRows) startTime?.let { "$it:00" } else null

private fun ScheduleDraft.endTimeForDb(): String? =
    if (showsTimeRows) endTime?.let { "$it:00" } else null

/**
 * 서버 row → UI draft. 반복 규칙 · 종료일은 별건 조회로 [rule], [endDate] 전달받아 병합.
 * DB `owner_kind` 는 creator 관점이라 [viewerId] 로 뷰어 관점으로 되돌린다 (파트너가 만든 걸 내가 보면 me↔partner 스왑).
 * [ScheduleDraft.createdBy] 에 원본 값을 유지해 편집 후 [SchedulesRepository.update] 가 다시 creator 관점으로 저장 가능.
 */
private fun SchedulesRepository.Row.toDraft(
    rule: RepeatRule,
    repeatEnd: LocalDate?,
    viewerId: String?,
): ScheduleDraft {
    val resolved = when {
        ownerKind == "us" || viewerId == null -> ownerKind
        createdBy == viewerId -> ownerKind
        ownerKind == "me" -> "partner"
        ownerKind == "partner" -> "me"
        else -> ownerKind
    }
    return ScheduleDraft(
        title = title,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate),
        type = if (type == "task") ScheduleType.Task else ScheduleType.Schedule,
        allDay = allDay,
        startTime = startTime?.take(5),   // "HH:MM:SS" → "HH:MM"
        endTime = endTime?.take(5),
        repeat = rule,
        repeatEndDate = repeatEnd,
        owner = when (resolved) {
            "me" -> ScheduleOwner.Me
            "partner" -> ScheduleOwner.Partner
            else -> ScheduleOwner.Us
        },
        isPrivate = isPrivate,
        createdBy = createdBy,
        seriesId = seriesId,
    )
}

/**
 * `RepeatRule` → `RepeatRow`. `None` 은 null 반환.
 * Weekly 는 요일 미선택 시 저장 스킵 (CHECK weekly_days IS NOT NULL 위배).
 */
private fun RepeatRule.toRow(scheduleId: String, endDate: LocalDate?): SchedulesRepository.RepeatRow? = when (this) {
    RepeatRule.None -> null
    RepeatRule.Daily -> SchedulesRepository.RepeatRow(
        scheduleId = scheduleId, kind = "daily", endsAt = endDate?.toString(),
    )
    is RepeatRule.Weekly -> {
        if (days.isEmpty()) null
        else SchedulesRepository.RepeatRow(
            scheduleId = scheduleId,
            kind = "weekly",
            weeklyDays = days.toWeeklyBitmask(),
            endsAt = endDate?.toString(),
        )
    }
    is RepeatRule.Monthly -> SchedulesRepository.RepeatRow(
        scheduleId = scheduleId,
        kind = "monthly",
        monthlyDay = day.toShort(),
        endsAt = endDate?.toString(),
    )
    is RepeatRule.Yearly -> SchedulesRepository.RepeatRow(
        scheduleId = scheduleId,
        kind = "yearly",
        yearlyMonth = month.toShort(),
        yearlyDay = day.toShort(),
        endsAt = endDate?.toString(),
    )
}

/** `RepeatRow` → `RepeatRule`. 알 수 없는 kind 는 [RepeatRule.None] 폴백. */
private fun SchedulesRepository.RepeatRow.toRule(): RepeatRule = when (kind) {
    "daily" -> RepeatRule.Daily
    "weekly" -> RepeatRule.Weekly(days = (weeklyDays ?: 0).fromWeeklyBitmask())
    "monthly" -> RepeatRule.Monthly(day = (monthlyDay ?: 1).toInt())
    "yearly" -> RepeatRule.Yearly(month = (yearlyMonth ?: 1).toInt(), day = (yearlyDay ?: 1).toInt())
    else -> RepeatRule.None
}

/** `Set<DayOfWeek>` → 7비트 mask. `DayOfWeek.ordinal` = MONDAY..SUNDAY = 0..6. */
private fun Set<DayOfWeek>.toWeeklyBitmask(): Short {
    var mask = 0
    for (day in this) mask = mask or (1 shl day.ordinal)
    return mask.toShort()
}

private fun Short.fromWeeklyBitmask(): Set<DayOfWeek> {
    val bits = toInt()
    return DayOfWeek.entries.filterTo(mutableSetOf()) { bits and (1 shl it.ordinal) != 0 }
}

/**
 * 시리즈 id 로 쓸 UUID. platform 의존 없이 kotlinx.uuid 도 안 쓰고 간단히 random 16 bytes 를 hex 로.
 * Supabase 는 문자열이면 UUID 타입에 그대로 들어감 (postgres 가 파싱).
 */
private fun randomUuid(): String {
    val bytes = Random.Default.nextBytes(16)
    // RFC 4122 v4 markers
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = bytes.joinToString("") { ((it.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
}
