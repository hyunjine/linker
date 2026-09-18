package com.hyunjine.linker.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.hyunjine.linker.data.remote.AnniversariesRepository
import com.hyunjine.linker.data.remote.CoupleRealtimeSubscription
import com.hyunjine.linker.data.remote.subscribeCoupleRealtime
import kotlinx.datetime.LocalDate

/**
 * `couple_anniversaries` 를 로드해 [year] 에 표시할 [CalendarDayEntry] 맵으로 변환한다.
 *
 * 규칙:
 *  - `repeatYearly = true` → 원본 date 의 (month, day) 를 [year] 로 옮겨 배치. 2월 29일은
 *    평년에는 표시 스킵 (문화적으로는 "3월 1일로 이동" 도 흔하지만, 이번 스코프에서는 skip
 *    으로 결정 · 반복 UX 재설계 이슈로 이월).
 *  - `repeatYearly = false` → 원본 date 가 [year] 와 같을 때만 배치.
 *
 * chip 라벨:
 *  - `repeatYearly = false` → 제목만.
 *  - `repeatYearly = true` 이며 [year] > base year → `"제목 · N주년"` (0주년은 생략).
 *
 * 실시간:
 *  - 같은 프로세스 안 `AnniversariesViewModel` 이 자체 realtime 구독을 갖지만, MainScreen 은
 *    별도 라이프사이클이므로 여기서도 `couple_anniversaries` 변경 시 자동 재fetch 되도록
 *    [subscribeCoupleRealtime] 을 붙인다. 첫 프레임엔 빈 맵.
 *
 * @param year 현재 화면에 노출된 연도. 바뀌면 자동 재계산.
 * @return `LocalDate -> CalendarDayEntry` 매핑. MainScreen 의 mergeEntries 로 병합.
 */
@Composable
fun rememberAnniversaryEntries(year: Int): Map<LocalDate, CalendarDayEntry> {
    var rows by remember { mutableStateOf<List<AnniversariesRepository.Row>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    // 채널 재구독은 프로세스 라이프사이클 동안 한 번이면 충분. couple 변경 (join · unlink) 시엔
    // MainRoute 가 coupleRefreshTick 을 올리는데, 그 시점에 이 Composable 은 재composition 될 뿐
    // 채널을 다시 열 필요는 없다 (subscribeCoupleRealtime 이 내부적으로 새 couple_id 로 채널을 만듦).
    DisposableEffect(Unit) {
        val job = scope.subscribeCoupleRealtime(
            CoupleRealtimeSubscription(onAnniversariesChanged = { refreshTick++ }),
        )
        onDispose { job.cancel() }
    }

    LaunchedEffect(refreshTick) {
        runCatching { AnniversariesRepository.list() }
            .onSuccess { rows = it }
            .onFailure { println("[Anniv/Chip] list 실패: $it") }
    }

    return remember(rows, year) { rows.expandForYear(year) }
}

/**
 * 목록을 [year] 관점으로 확장. 하루에 여러 기념일이 걸리면 이어 붙여 하나의 [CalendarDayEntry]
 * 로 병합 (chip 우선순위는 상위 mergeEntries 가 정렬).
 */
private fun List<AnniversariesRepository.Row>.expandForYear(year: Int): Map<LocalDate, CalendarDayEntry> {
    if (isEmpty()) return emptyMap()
    val acc = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    for (row in this) {
        val base = runCatching { LocalDate.parse(row.date) }.getOrNull() ?: continue
        val display = if (row.repeatYearly) {
            // 매년 반복 → year 로 옮기기. 2/29 는 평년 skip.
            runCatching { LocalDate(year, base.month, base.day) }.getOrNull()
        } else {
            // 단발성 → 해당 year 에만 표시.
            if (base.year == year) base else null
        } ?: continue

        val label = row.chipLabel(year, base)
        acc.getOrPut(display) { mutableListOf() }.add(
            CalendarEvent(
                label = label,
                type = CalendarEventType.Anniversary,
                id = "anniv:${row.id}:$year", // 월별 캐시 병합에서 dedupe 되도록 unique key
            ),
        )
    }
    return acc.mapValues { CalendarDayEntry(events = it.value.toList()) }
}

/**
 * 반복 기념일이면 `"제목 · N주년"` (N ≥ 1), 아니면 원본 제목만.
 * 재설계 시 표기 규칙 (예: 100일 카운트 · D-Day) 은 별도 이슈로.
 */
private fun AnniversariesRepository.Row.chipLabel(displayYear: Int, base: LocalDate): String {
    if (!repeatYearly) return title
    val n = displayYear - base.year
    return if (n >= 1) "$title · ${n}주년" else title
}
