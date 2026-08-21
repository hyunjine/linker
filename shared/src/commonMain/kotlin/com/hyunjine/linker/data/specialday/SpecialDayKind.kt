package com.hyunjine.linker.data.specialday

import com.hyunjine.linker.ui.main.CalendarEventType

/**
 * 특일 카테고리. [SpecialDayApi.fetchYear] 가 이 값에 따라 어느 소스에서 무엇을 가져올지 결정한다.
 *
 * @property eventType 이 카테고리의 항목을 [com.hyunjine.linker.ui.main.CalendarEvent] 로 만들 때 쓸 색.
 */
enum class SpecialDayKind(
    val eventType: CalendarEventType,
) {
    /** 공휴일 (대체공휴일 포함). nager.date PublicHolidays 소스. */
    Holiday(eventType = CalendarEventType.Holiday),

    /** 24절기. 현재는 소스 미확보로 항상 빈 리스트 반환 — 드로워 토글은 표시상 유지. */
    SolarTerm(eventType = CalendarEventType.Season),
}
