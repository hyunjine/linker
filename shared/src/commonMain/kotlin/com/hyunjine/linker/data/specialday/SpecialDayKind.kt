package com.hyunjine.linker.data.specialday

import com.hyunjine.linker.ui.main.CalendarEventType

/**
 * data.go.kr `SpcdeInfoService` 의 엔드포인트 카테고리. 같은 서비스 안에 여러 엔드포인트가 있고
 * 응답 스키마는 공통 (`locdate` / `dateName` / `isHoliday`), 우리가 캘린더 chip 으로 어떻게 그릴지만 다르다.
 *
 * @property endpoint API path 마지막 조각 (`.../SpcdeInfoService/{endpoint}`).
 * @property eventType 이 카테고리의 항목을 [com.hyunjine.linker.ui.main.CalendarEvent] 로 만들 때 쓸 색.
 * @property includeAll `true` 면 응답 항목 전부 포함, `false` 면 `isHoliday=="Y"` 만.
 *   국경일·공휴일은 대체공휴일 여부가 중요해서 필터 적용, 절기는 정의상 `isHoliday=="N"` 이라 필터 없이 전체 포함.
 */
enum class SpecialDayKind(
    val endpoint: String,
    val eventType: CalendarEventType,
    val includeAll: Boolean,
) {
    Holiday(
        endpoint = "getRestDeInfo",
        eventType = CalendarEventType.Holiday,
        includeAll = false,
    ),
    SolarTerm(
        endpoint = "get24DivisionsInfo",
        eventType = CalendarEventType.Season,
        includeAll = true,
    ),
    // 후속: SundryDay ("getSundryDayInfo"), Anniversary ("getAnniversaryInfo") 등
}
