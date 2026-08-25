package com.hyunjine.linker.data.remote

import com.hyunjine.linker.ui.schedule.RepeatRule
import com.hyunjine.linker.ui.schedule.ScheduleDraft
import com.hyunjine.linker.ui.schedule.ScheduleOwner
import com.hyunjine.linker.ui.schedule.ScheduleType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `public.schedules` CRUD. RLS `schedules_all_in_my_couple` 로 내 커플 스케줄만 접근 가능.
 * couple_id 는 진입 시 한 번 조회해서 캐시 (`myCoupleId()`).
 *
 * 반복 규칙 (`schedule_repeat_rules`) 저장은 이번 스코프 밖 — draft.repeat 필드는 무시된다.
 * 후속 이슈에서 weekly_days 비트마스크 · 종료조건 매핑 추가.
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

    private var cachedCoupleId: String? = null

    /** 현재 유저의 couple_id. 없으면 null (아직 커플 미가입). 첫 호출 시 조회 후 캐시. */
    suspend fun myCoupleId(): String? {
        cachedCoupleId?.let { return it }
        return CouplesRepository.myCoupleIdOrNull()?.also { cachedCoupleId = it }
    }

    /**
     * 단일 스케줄 조회 후 UI draft 로 변환. 없거나 조회 실패 시 null.
     * 반복 규칙 (`schedule_repeat_rules`) 은 #52 후속 — 현재는 항상 [RepeatRule.None].
     */
    suspend fun getDraftById(id: String): ScheduleDraft? {
        val row = SupabaseProvider.client.from("schedules")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<Row>() ?: return null
        return row.toDraft()
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
     * 새 스케줄 저장. 반환값은 생성된 id.
     * @throws IllegalStateException 커플에 속하지 않은 경우.
     */
    suspend fun create(draft: ScheduleDraft): String {
        val coupleId = myCoupleId() ?: error("커플에 속하지 않은 유저가 스케줄 저장 시도")
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("세션 없이 스케줄 저장 시도")
        val inserted = SupabaseProvider.client.from("schedules")
            .insert(draft.toInsertRow(coupleId = coupleId, createdBy = uid)) {
                select()
            }
            .decodeSingle<Row>()
        return inserted.id
    }

    /** 기존 스케줄 갱신. draft 의 모든 필드를 그대로 UPDATE. */
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
    }

    suspend fun delete(id: String) {
        SupabaseProvider.client.from("schedules").delete {
            filter { eq("id", id) }
        }
    }
}

/** draft → INSERT payload. RLS `schedules_all_in_my_couple` 이 `created_by = auth.uid()` 를 요구하므로 명시 포함. */
private fun ScheduleDraft.toInsertRow(coupleId: String, createdBy: String): SchedulesRepository.Row =
    SchedulesRepository.Row(
        id = "00000000-0000-0000-0000-000000000000", // 서버 default 로 덮어써짐. Serializable 요구 필드
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
        isDone = false,
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

/** 서버 row → UI draft. 반복 규칙은 아직 저장 · 조회 안 하므로 [RepeatRule.None]. */
private fun SchedulesRepository.Row.toDraft(): ScheduleDraft = ScheduleDraft(
    title = title,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    type = if (type == "task") ScheduleType.Task else ScheduleType.Schedule,
    allDay = allDay,
    startTime = startTime?.take(5),   // "HH:MM:SS" → "HH:MM"
    endTime = endTime?.take(5),
    repeat = RepeatRule.None,
    owner = when (ownerKind) {
        "me" -> ScheduleOwner.Me
        "partner" -> ScheduleOwner.Partner
        else -> ScheduleOwner.Us
    },
)
