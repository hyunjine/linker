package com.hyunjine.linker.feature.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 일정의 성격. Figma 세그먼트 UI 는 두 항목 (`할 일` / `일정`) 만 노출한다.
 * 종일 vs 시간 일정 구분은 [ScheduleDraft.allDay] 스위치로 처리.
 */
enum class ScheduleType(val label: String) {
    /** 완료 체크 가능한 항목. 택배 보내기, 이력서 내기 등. */
    Task("할 일"),

    /** 시간 or 종일 일정 (allDay 스위치로 세부 구분). 약속·병원·여행·기념일 등. */
    Schedule("일정"),
}

/** 일정 소유자. 편집·삭제 권한 판단에 사용. */
enum class ScheduleOwner(val label: String) {
    Me("나"),

    /** 상대방 일정은 조회만 가능 (편집·삭제 UI 비활성). */
    Partner("상대방"),

    /** 양쪽 다 편집·삭제 가능. */
    Us("공동"),
}

/**
 * 반복 시리즈의 인스턴스를 편집할 때 적용 범위 선택.
 * "취소" 는 저장 자체를 안 하므로 이 enum 에 없다.
 */
enum class SeriesEditScope {
    /** 이 스케줄만: 시리즈에서 인스턴스를 detach 해 단독 row 로 저장. 반복 규칙 변경은 이 케이스에서 무시. */
    OnlyThis,

    /** 이후 모든 반복: 현재 인스턴스 이후 (start_date >= 현재) 시리즈 rows 에 일괄 반영. */
    ThisAndFuture,
}

/**
 * 반복 규칙. 매주는 요일 다중 선택, 매월/매년은 기준 날짜 저장.
 *
 * 종료 날짜는 [ScheduleDraft.repeatEndDate] 로 별도 필드 관리 — 규칙 자체와 분리해서
 * 다루면 "규칙 유지 + 종료일만 변경" 같은 편집 시나리오도 자연스럽게 처리된다.
 */
sealed interface RepeatRule {
    val label: String

    object None : RepeatRule { override val label = "반복 안함" }
    object Daily : RepeatRule { override val label = "매일" }
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule { override val label = "매주" }
    data class Monthly(val day: Int) : RepeatRule { override val label = "매월" }
    data class Yearly(val month: Int, val day: Int) : RepeatRule { override val label = "매년" }

    companion object {
        /** RepeatPickerSheet 상단 5개 옵션. */
        val Options: List<RepeatRule> = listOf(None, Daily, Weekly(emptySet()), Monthly(1), Yearly(1, 1))
    }
}

/**
 * 편집 중인 일정의 draft 상태. 화면이 소유하고 저장 시점에 검증·커밋.
 *
 * [allDay] 는 [type] == [ScheduleType.Schedule] 일 때만 의미. `false` 면 시각 행이 노출된다.
 * [type] == [ScheduleType.Task] 이면 시각 행은 항상 감춤.
 * [startTime]/[endTime] 은 "HH:MM" (24h) 문자열, 5분 스텝. null 이면 기본값 사용.
 *
 * [repeat] 가 [RepeatRule.None] 이 아니면 [repeatEndDate] 를 반드시 함께 지정
 * (RepeatPickerSheet 이 wheel picker 로 강제). 저장 시 이 종료일까지의 인스턴스를
 * 미리 materialize 해서 시리즈로 삽입한다.
 */
data class ScheduleDraft(
    val title: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: ScheduleType = ScheduleType.Schedule,
    val allDay: Boolean = false,
    val startTime: String? = defaultStartTimeNow(),
    val endTime: String? = defaultEndTimeNow(),
    val repeat: RepeatRule = RepeatRule.None,
    val repeatEndDate: LocalDate? = null,
    val owner: ScheduleOwner = ScheduleOwner.Me,
    /**
     * 비공개 여부. `true` 면 파트너에게 SELECT 자체가 안 되도록 RLS 가 감춘다.
     * `owner = Us` (공동) 와는 논리적 모순이라 UI 가 상호 배제한다.
     */
    val isPrivate: Boolean = false,
    /**
     * 원본 row 의 created_by. 신규는 null. 편집 시 UPDATE 에서 owner 를 creator 관점으로 되돌릴 때 사용.
     * DB `owner_kind` 는 creator 관점으로 저장되므로, 파트너가 만든 걸 내가 편집해서 owner 를
     * 바꿔 저장할 때 me<->partner 스왑이 필요하다.
     */
    val createdBy: String? = null,
    /**
     * 이 draft 가 반복 시리즈의 일원이면 해당 series_id. 편집 저장 시 시리즈 batch 처리 여부 판단에 사용.
     */
    val seriesId: String? = null,
) {
    val isEditableByCurrentUser: Boolean get() = owner != ScheduleOwner.Partner

    /** 시각 행을 UI 에 노출할지. Schedule 유형에서 종일이 아닐 때만 true. */
    val showsTimeRows: Boolean get() = type == ScheduleType.Schedule && !allDay

    /** 종일 토글 UI 를 노출할지 (할 일 유형에서는 개념상 필요 없음). */
    val showsAllDayToggle: Boolean get() = type == ScheduleType.Schedule
}

/**
 * 신규 draft 의 시작 시각 기본값. 현재 시각을 5분 단위로 올림해 "HH:MM" 로 반환.
 * 예: 14:15 → "14:15", 14:16~14:19 → "14:20". picker 가 5분 스텝이라 사용자가
 * 실제 스크롤 없이 첫 노출 값이 그대로 유효.
 */
@OptIn(ExperimentalTime::class)
internal fun defaultStartTimeNow(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    val total = now.hour * 60 + now.minute
    val rounded = ((total + 4) / 5) * 5 % (24 * 60)
    return formatHhMm(rounded)
}

/** 신규 draft 의 종료 시각 기본값. 시작 시각 + 1시간 (24시간 wrap). */
internal fun defaultEndTimeNow(): String = plusOneHourHhMm(defaultStartTimeNow())

private fun plusOneHourHhMm(hhmm: String): String {
    val p = hhmm.split(':')
    val total = (p[0].toInt() * 60 + p[1].toInt() + 60) % (24 * 60)
    return formatHhMm(total)
}

private fun formatHhMm(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
}
