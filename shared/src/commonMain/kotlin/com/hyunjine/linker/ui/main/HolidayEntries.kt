package com.hyunjine.linker.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hyunjine.linker.data.holiday.HolidayRepository
import kotlinx.datetime.LocalDate

/**
 * [year] 에 해당하는 대한민국 공휴일을 특일정보 API 에서 가져와 [CalendarDayEntry] 맵으로 반환한다.
 * `isHoliday == "Y"` 인 항목만 유리 chip 대상 (24절기·기념일 중 비공휴일은 제외).
 *
 * 첫 프레임엔 빈 맵이고 fetch 성공 시 state 갱신 → recomposition. 실패해도 빈 맵으로 그대로 유지.
 * Repository 는 프로세스 라이프사이클 동안 연도별로 캐시하므로 같은 연 재요청은 즉시 반환.
 */
@Composable
fun rememberHolidayEntries(
    year: Int,
    repository: HolidayRepository = remember { HolidayRepository() },
): Map<LocalDate, CalendarDayEntry> {
    var entries by remember { mutableStateOf<Map<LocalDate, CalendarDayEntry>>(emptyMap()) }
    LaunchedEffect(year) {
        val holidays = repository.getYear(year).filter { it.isHoliday }
        entries = holidays.associate { dto ->
            val y = dto.locdate / 10000
            val m = (dto.locdate / 100) % 100
            val d = dto.locdate % 100
            LocalDate(y, m, d) to CalendarDayEntry(
                events = listOf(CalendarEvent(dto.dateName, CalendarEventType.Holiday)),
            )
        }
    }
    return entries
}
