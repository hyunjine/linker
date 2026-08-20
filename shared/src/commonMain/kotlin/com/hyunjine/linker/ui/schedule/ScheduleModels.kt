package com.hyunjine.linker.ui.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** 일정의 성격. UI 상 세그먼트로 노출되고, 노출되는 입력 필드 (시각 유무) 를 결정. */
enum class ScheduleType(val label: String) {
    /** 시각 필수. 약속·병원·영화 등. */
    Timed("시간 일정"),

    /** 시각 없이 하루 이상 지속. 여행·생일·기념일 등. */
    AllDay("종일 일정"),

    /** 시각 없이 처리해야 하는 항목. 완료 체크 가능. */
    Task("할 일"),
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
 * 매월/매년의 [day]/[month] 는 저장 시 스케줄의 시작일에서 자동 유도해도 되지만,
 * UI 에서 다른 값을 명시적으로 고를 수 있게 sealed 로 분리해 둠.
 */
sealed interface RepeatRule {
    val label: String

    object None : RepeatRule { override val label = "안 함" }
    object Daily : RepeatRule { override val label = "매일" }
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule { override val label = "매주" }
    data class Monthly(val day: Int) : RepeatRule { override val label = "매월" }
    data class Yearly(val month: Int, val day: Int) : RepeatRule { override val label = "매년" }

    companion object {
        /** 옵션 시트에서 뿌릴 순서. */
        val Options: List<RepeatRule> = listOf(None, Daily, Weekly(emptySet()), Monthly(1), Yearly(1, 1))
    }
}

/**
 * 편집 중인 일정의 draft 상태. 화면이 소유하고 저장 시점에 검증·커밋.
 * [startTime]/[endTime] 은 [ScheduleType.Timed] 일 때만 유의미. UI 는 유형에 따라 필드를 감춤.
 */
data class ScheduleDraft(
    val title: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: ScheduleType = ScheduleType.Timed,
    /** "HH:MM" 형식 (24h). null 이면 미설정. 실제 시각은 5분 스텝. */
    val startTime: String? = "10:00",
    val endTime: String? = "11:00",
    val repeat: RepeatRule = RepeatRule.None,
    val owner: ScheduleOwner = ScheduleOwner.Me,
) {
    val isEditableByCurrentUser: Boolean get() = owner != ScheduleOwner.Partner
}
