package com.hyunjine.linker.data.remote

import com.hyunjine.linker.feature.schedule.RepeatRule
import com.hyunjine.linker.feature.schedule.ScheduleDraft
import com.hyunjine.linker.feature.schedule.ScheduleOwner
import com.hyunjine.linker.feature.schedule.ScheduleType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `public.schedules` CRUD. RLS `schedules_all_in_my_couple` 로 내 커플 스케줄만 접근 가능.
 * couple_id 는 매 호출마다 fresh 조회 (`myCoupleId()`).
 *
 * 반복 규칙 (`schedule_repeat_rules`) 은 `save` / `update` 에서 함께 upsert · delete.
 * `weekly_days` 는 bitmask (MON=1<<0..SUN=1<<6, kotlinx `DayOfWeek.ordinal` 기준).
 * 종료조건 (`ends_at` / `max_count`) 는 UI 미노출로 이번 스코프 밖.
 */
object SchedulesRepository {

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
    )

    /**
     * INSERT 전용 payload. `Row` 를 그대로 쓰면 `id = "00000000-..."` placeholder 가 실제 값으로
     * 서버에 기록되어 (Postgres DEFAULT 는 컬럼 명시 시 발동하지 않음) PK 유니크 위반 · 캘린더에
     * placeholder UUID 저장이 발생. id · is_done · created_at · updated_at 은 서버 DEFAULT 로 채워지므로
     * 여기서 아예 필드를 뺀다.
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
    )

    /** `schedule_repeat_rules` row. 필요한 필드만 nullable — CHECK 제약은 서버가 검증. */
    @Serializable
    data class RepeatRow(
        @SerialName("schedule_id") val scheduleId: String,
        val kind: String, // 'daily' | 'weekly' | 'monthly' | 'yearly' | 'custom'
        @SerialName("weekly_days") val weeklyDays: Short? = null,
        @SerialName("monthly_day") val monthlyDay: Short? = null,
        @SerialName("yearly_month") val yearlyMonth: Short? = null,
        @SerialName("yearly_day") val yearlyDay: Short? = null,
        @SerialName("custom_rule") val customRule: String? = null,
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
        val repeat = SupabaseProvider.client.from("schedule_repeat_rules")
            .select { filter { eq("schedule_id", id) } }
            .decodeSingleOrNull<RepeatRow>()
            ?.toRule() ?: RepeatRule.None
        return row.toDraft(repeat)
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
     * 새 스케줄 저장. 반환값은 생성된 id. 반복 규칙도 함께 삽입 (draft.repeat != None 일 때).
     */
    suspend fun create(draft: ScheduleDraft): String {
        val coupleId = myCoupleId() ?: error("커플에 속하지 않은 유저가 스케줄 저장 시도")
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("세션 없이 스케줄 저장 시도")
        val inserted = SupabaseProvider.client.from("schedules")
            .insert(draft.toInsertPayload(coupleId = coupleId, createdBy = uid)) {
                select()
            }
            .decodeSingle<Row>()
        applyRepeatRule(inserted.id, draft.repeat)
        return inserted.id
    }

    /** 기존 스케줄 갱신. 본체 UPDATE 후 반복 규칙도 upsert/delete 로 재정렬. */
    suspend fun update(id: String, draft: ScheduleDraft) {
        SupabaseProvider.client.from("schedules").update({
            set("type", draft.type.toDbValue())
            set("owner_kind", draft.owner.toDbValue())
            set("title", draft.title)
            set("start_date", draft.startDate.toString())
            set("end_date", draft.endDate.toString())
            set("all_day", draft.allDay)
            set("start_time", draft.startTimeForDb())
            set("end_time", draft.endTimeForDb())
        }) {
            filter { eq("id", id) }
        }
        applyRepeatRule(id, draft.repeat)
    }

    suspend fun delete(id: String) {
        // schedule_repeat_rules 는 FK ON DELETE CASCADE 로 함께 삭제됨.
        SupabaseProvider.client.from("schedules").delete {
            filter { eq("id", id) }
        }
    }

    /** 할 일 (`type='task'`) 의 `is_done` 만 단일 컬럼 갱신. DayDetailSheet 체크박스 토글에서 사용. */
    suspend fun setTaskDone(id: String, done: Boolean) {
        SupabaseProvider.client.from("schedules").update({
            set("is_done", done)
        }) {
            filter { eq("id", id) }
        }
    }

    /**
     * `rule` 이 [RepeatRule.None] 이면 기존 규칙 삭제, 그 외에는 upsert.
     * schedule_repeat_rules 는 schedule_id 가 PK 라 upsert 로 idempotent.
     */
    private suspend fun applyRepeatRule(scheduleId: String, rule: RepeatRule) {
        if (rule == RepeatRule.None) {
            SupabaseProvider.client.from("schedule_repeat_rules").delete {
                filter { eq("schedule_id", scheduleId) }
            }
            return
        }
        val row = rule.toRow(scheduleId) ?: return  // Custom without payload 등 저장 스킵
        SupabaseProvider.client.from("schedule_repeat_rules").upsert(row)
    }
}

/** draft → INSERT payload. RLS `schedules_all_in_my_couple` 이 `created_by = auth.uid()` 를 요구하므로 명시 포함. */
private fun ScheduleDraft.toInsertPayload(coupleId: String, createdBy: String) =
    SchedulesRepository.InsertPayload(
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

/** 서버 row → UI draft. 반복 규칙은 별건 조회로 [rule] 전달받아 병합. */
private fun SchedulesRepository.Row.toDraft(rule: RepeatRule): ScheduleDraft = ScheduleDraft(
    title = title,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    type = if (type == "task") ScheduleType.Task else ScheduleType.Schedule,
    allDay = allDay,
    startTime = startTime?.take(5),   // "HH:MM:SS" → "HH:MM"
    endTime = endTime?.take(5),
    repeat = rule,
    owner = when (ownerKind) {
        "me" -> ScheduleOwner.Me
        "partner" -> ScheduleOwner.Partner
        else -> ScheduleOwner.Us
    },
)

/**
 * `RepeatRule` → `RepeatRow`. `None` · 저장 못 하는 형태 (예: Custom without payload) 는 null 반환.
 * Custom 은 아직 UI 에서 세부 규칙을 못 입력해 저장 스킵 (스키마의 `custom_rule NOT NULL` 위배).
 */
private fun RepeatRule.toRow(scheduleId: String): SchedulesRepository.RepeatRow? = when (this) {
    RepeatRule.None -> null
    RepeatRule.Daily -> SchedulesRepository.RepeatRow(scheduleId = scheduleId, kind = "daily")
    is RepeatRule.Weekly -> {
        // weekly_days IS NOT NULL 이 CHECK 요건. days 가 비어있으면 저장 스킵.
        if (days.isEmpty()) null
        else SchedulesRepository.RepeatRow(
            scheduleId = scheduleId,
            kind = "weekly",
            weeklyDays = days.toWeeklyBitmask(),
        )
    }
    is RepeatRule.Monthly -> SchedulesRepository.RepeatRow(
        scheduleId = scheduleId,
        kind = "monthly",
        monthlyDay = day.toShort(),
    )
    is RepeatRule.Yearly -> SchedulesRepository.RepeatRow(
        scheduleId = scheduleId,
        kind = "yearly",
        yearlyMonth = month.toShort(),
        yearlyDay = day.toShort(),
    )
    RepeatRule.Custom -> null  // 세부 규칙 UI 붙기 전엔 저장 스킵
}

/** `RepeatRow` → `RepeatRule`. 알 수 없는 kind 는 [RepeatRule.None] 폴백. */
private fun SchedulesRepository.RepeatRow.toRule(): RepeatRule = when (kind) {
    "daily" -> RepeatRule.Daily
    "weekly" -> RepeatRule.Weekly(days = (weeklyDays ?: 0).fromWeeklyBitmask())
    "monthly" -> RepeatRule.Monthly(day = (monthlyDay ?: 1).toInt())
    "yearly" -> RepeatRule.Yearly(month = (yearlyMonth ?: 1).toInt(), day = (yearlyDay ?: 1).toInt())
    "custom" -> RepeatRule.Custom
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
