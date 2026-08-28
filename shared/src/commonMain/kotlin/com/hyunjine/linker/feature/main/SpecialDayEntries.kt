package com.hyunjine.linker.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hyunjine.linker.data.specialday.SpecialDayDto
import com.hyunjine.linker.data.specialday.SpecialDayKind
import com.hyunjine.linker.data.specialday.SpecialDayRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

/**
 * [year] 에 대해 지정한 [kinds] 특일들을 병렬로 가져와 하나의 [CalendarDayEntry] 맵으로 병합한다.
 * 같은 날짜에 여러 종류가 걸리면 events 를 이어붙여 chip 이 우선순위 순으로 정렬돼 표시된다
 * (예: 광복절 공휴일 + 그 날이 절기와 겹치는 경우).
 *
 * 첫 프레임엔 빈 맵. fetch 성공 시 state 갱신 → recomposition. 실패 kind 는 조용히 스킵.
 * Repository 는 프로세스 라이프사이클 동안 (year, kind) 별로 캐시하므로 같은 요청은 즉시 반환.
 */
@Composable
fun rememberSpecialDayEntries(
    year: Int,
    vararg kinds: SpecialDayKind,
    repository: SpecialDayRepository = remember { SpecialDayRepository() },
): Map<LocalDate, CalendarDayEntry> {
    val kindsKey = remember(kinds) { kinds.toList() }
    var entries by remember { mutableStateOf<Map<LocalDate, CalendarDayEntry>>(emptyMap()) }
    LaunchedEffect(year, kindsKey) {
        entries = coroutineScope {
            kindsKey
                .map { kind -> async { kind to repository.getYear(year, kind) } }
                .awaitAll()
                .fold(mutableMapOf<LocalDate, CalendarDayEntry>()) { acc, (kind, dtos) ->
                    for (dto in dtos.filterVisible(kind)) {
                        val date = dto.toLocalDate() ?: continue
                        val ev = CalendarEvent(dto.dateName, kind.eventType)
                        val existing = acc[date]
                        acc[date] = if (existing == null) {
                            CalendarDayEntry(events = listOf(ev))
                        } else {
                            existing.copy(events = existing.events + ev)
                        }
                    }
                    acc
                }
        }
    }
    return entries
}

private fun List<SpecialDayDto>.filterVisible(kind: SpecialDayKind): List<SpecialDayDto> =
    if (kind.includeAll) this else filter { it.isHoliday }

private fun SpecialDayDto.toLocalDate(): LocalDate? {
    if (locdate <= 0) return null
    val y = locdate / 10000
    val m = (locdate / 100) % 100
    val d = locdate % 100
    return runCatching { LocalDate(y, m, d) }.getOrNull()
}
