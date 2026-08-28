package com.hyunjine.linker.feature.main

import com.hyunjine.linker.data.remote.SchedulesRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 스케줄 rows → DayDetailSheet 가 소비하는 [DayDetail].
 * - type='task' → [DayTask]
 * - type='schedule' + all_day → [AllDaySchedule]
 * - type='schedule' + 시각 → [TimedSchedule]
 */
internal fun List<SchedulesRepository.Row>.toDayDetail(date: LocalDate): DayDetail {
    val tasks = mutableListOf<DayTask>()
    val timed = mutableListOf<TimedSchedule>()
    val allDay = mutableListOf<AllDaySchedule>()
    for (row in this) {
        val owner = row.ownerKind.toDayOwner()
        when {
            row.type == "task" -> tasks += DayTask(
                id = row.id, title = row.title, isDone = row.isDone, owner = owner,
            )
            row.allDay -> allDay += AllDaySchedule(
                id = row.id, title = row.title, owner = owner, barColor = null,
            )
            else -> timed += TimedSchedule(
                id = row.id,
                startTime = row.startTime.toKoreanClock() ?: "",
                endTime = row.endTime.toKoreanClock(),
                title = row.title,
                owner = owner,
            )
        }
    }
    return DayDetail(
        date = date,
        lunarLabel = null,   // 음력 표시는 후속 이슈
        tasks = tasks,
        timedSchedules = timed,
        allDaySchedules = allDay,
    )
}

/**
 * 서버에서 받은 스케줄 rows 를 MainScreen 이 소비하는 `Map<LocalDate, CalendarDayEntry>` 로 변환.
 * 스케줄이 [start_date, end_date] 범위를 커버하면 각 날짜에 chip 을 추가한다.
 * chip 색은 [ownerColors] 로 결정 (me/partner/us).
 */
internal fun List<SchedulesRepository.Row>.toCalendarEntries(
    ownerColors: OwnerColors,
): Map<LocalDate, CalendarDayEntry> {
    val out = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    for (row in this) {
        val start = LocalDate.parse(row.startDate)
        val end = LocalDate.parse(row.endDate)
        val tint = ownerColors.forOwner(row.ownerKind)
        var d = start
        while (d <= end) {
            out.getOrPut(d) { mutableListOf() }
                .add(CalendarEvent(row.title, CalendarEventType.Personal, tintColor = tint, id = row.id))
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return out.mapValues { CalendarDayEntry(events = it.value.toList()) }
}

internal fun String.toDayOwner(): DayOwner = when (this) {
    "me" -> DayOwner.Me
    "partner" -> DayOwner.Partner
    else -> DayOwner.Us
}

/** "HH:MM:SS" → "오전 10:00" / "오후 2:00" 형식. null 은 null 그대로. */
internal fun String?.toKoreanClock(): String? {
    if (this.isNullOrBlank()) return null
    val h = substring(0, 2).toIntOrNull() ?: return null
    val m = substring(3, 5)
    val (period, hour12) = when {
        h == 0 -> "오전" to 12
        h < 12 -> "오전" to h
        h == 12 -> "오후" to 12
        else -> "오후" to (h - 12)
    }
    return "$period $hour12:$m"
}
