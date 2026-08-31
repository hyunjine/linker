package com.hyunjine.linker.feature.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

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
 * 반복 규칙. 매주는 요일 다중 선택, 매월/매년은 기준 날짜 저장.
 */
sealed interface RepeatRule {
    val label: String

    object None : RepeatRule { override val label = "반복 안함" }
    object Daily : RepeatRule { override val label = "매일" }
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule { override val label = "매주" }
    data class Monthly(val day: Int) : RepeatRule { override val label = "매월" }
    data class Yearly(val month: Int, val day: Int) : RepeatRule { override val label = "매년" }

    /**
     * 사용자 정의 반복. 세부 규칙 편집 UI 는 후속 이슈 (예: N주마다 · 종료 조건 등).
     * 현재는 라벨 노출용 마커 값으로만 사용.
     */
    object Custom : RepeatRule { override val label = "사용자 설정" }

    companion object {
        val Options: List<RepeatRule> = listOf(None, Daily, Weekly(emptySet()), Monthly(1), Yearly(1, 1), Custom)
    }
}

/**
 * 편집 중인 일정의 draft 상태. 화면이 소유하고 저장 시점에 검증·커밋.
 *
 * [allDay] 는 [type] == [ScheduleType.Schedule] 일 때만 의미. `false` 면 시각 행이 노출된다.
 * [type] == [ScheduleType.Task] 이면 시각 행은 항상 감춤.
 * [startTime]/[endTime] 은 "HH:MM" (24h) 문자열, 5분 스텝. null 이면 기본값 사용.
 */
data class ScheduleDraft(
    val title: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: ScheduleType = ScheduleType.Schedule,
    val allDay: Boolean = false,
    val startTime: String? = "10:00",
    val endTime: String? = "11:00",
    val repeat: RepeatRule = RepeatRule.None,
    val owner: ScheduleOwner = ScheduleOwner.Me,
    /**
     * 원본 row 의 created_by. 신규는 null. 편집 시 UPDATE 에서 owner 를 creator 관점으로 되돌릴 때 사용.
     * DB `owner_kind` 는 creator 관점으로 저장되므로, 파트너가 만든 걸 내가 편집해서 owner 를
     * 바꿔 저장할 때 me<->partner 스왑이 필요하다.
     */
    val createdBy: String? = null,
) {
    val isEditableByCurrentUser: Boolean get() = owner != ScheduleOwner.Partner

    /** 시각 행을 UI 에 노출할지. Schedule 유형에서 종일이 아닐 때만 true. */
    val showsTimeRows: Boolean get() = type == ScheduleType.Schedule && !allDay

    /** 종일 토글 UI 를 노출할지 (할 일 유형에서는 개념상 필요 없음). */
    val showsAllDayToggle: Boolean get() = type == ScheduleType.Schedule
}
