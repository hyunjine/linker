package com.hyunjine.linker.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.liquidGlass
import com.hyunjine.linker.ui.theme.Background
import com.hyunjine.linker.ui.theme.CalendarLunarText
import com.hyunjine.linker.ui.theme.CalendarSaturday
import com.hyunjine.linker.ui.theme.CalendarSunday
import com.hyunjine.linker.ui.theme.CalendarTodayCircle
import com.hyunjine.linker.ui.theme.CalendarTodayText
import com.hyunjine.linker.ui.theme.CalendarWeekdayText
import com.hyunjine.linker.ui.theme.ChipHolidayBg
import com.hyunjine.linker.ui.theme.ChipHolidayText
import com.hyunjine.linker.ui.theme.ChipPersonalBg
import com.hyunjine.linker.ui.theme.ChipPersonalText
import com.hyunjine.linker.ui.theme.ChipSeasonBg
import com.hyunjine.linker.ui.theme.ChipSeasonText
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_down
import linker.shared.generated.resources.ic_menu
import linker.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource

/** 하루 셀에 표시할 이벤트 종류. 우선순위는 [priority] 로 결정 (낮을수록 먼저). */
enum class CalendarEventType {
    /** 법정 공휴일. 빨강 계열. */
    Holiday,

    /** 절기·잡절 등. 회색 계열. */
    Season,

    /** 커플/개인 일정. 분홍 계열. */
    Personal,
}

private val CalendarEventType.priority: Int
    get() = when (this) {
        CalendarEventType.Holiday -> 0
        CalendarEventType.Season -> 1
        CalendarEventType.Personal -> 2
    }

/** 하루 셀에 붙는 이벤트 chip 한 개. */
data class CalendarEvent(
    val label: String,
    val type: CalendarEventType,
)

/** 하루 셀에 붙는 부가 정보. `date` 를 키로 [MainScreen.entries] 에 담아 전달. */
data class CalendarDayEntry(
    val events: List<CalendarEvent> = emptyList(),
    /** 음력 표시 (예: "7.15"). 없으면 null. */
    val lunarLabel: String? = null,
)

/** 표시할 월. Figma 헤더 "YYYY. M" 형식에 그대로 대응. */
data class YearMonth(val year: Int, val month: Int) {
    /** 앞으로 [months] 개월 이동한 [YearMonth]. 음수면 뒤로. */
    fun plusMonths(months: Int): YearMonth {
        val total = year * 12L + (month - 1) + months
        val newYear = total.floorDiv(12).toInt()
        val newMonth = total.mod(12).toInt() + 1
        return YearMonth(newYear, newMonth)
    }

    companion object {
        fun current(now: LocalDate = today()): YearMonth = YearMonth(now.year, now.month.ordinal + 1)
    }
}

@OptIn(ExperimentalTime::class)
private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * 메인 화면. iOS 캘린더 스타일 월 그리드.
 *
 * 상단 [MainToolbar] (햄버거 · YYYY.M · 검색), 요일 헤더 [WeekdaysRow], 좌우 스와이프로 월 이동하는
 * [HorizontalPager] 로 6주 [CalendarGrid] 를 감쌈. 상호작용은 콜백만 노출 (이번 커밋에서 바텀시트/드로워는 미구현).
 *
 * @param initialYearMonth 최초 표시할 월. 기본은 현재 실제 이번 달. Pager 의 앵커.
 * @param today 오늘 날짜. 그리드에서 검정 원 마커로 강조. 기본은 시스템 오늘.
 * @param entries `LocalDate -> DayEntry` 매핑. 이벤트/음력 표시용. 없으면 빈 셀.
 * @param onMenuClick 좌상단 햄버거 탭.
 * @param onTitleClick 중앙 "YYYY. M v" 탭 (월 피커 열기).
 * @param onSearchClick 우상단 검색 탭.
 * @param onDayClick 그리드 셀 탭 (해당 날짜 상세 열기).
 */
@Composable
fun MainScreen(
    initialYearMonth: YearMonth = YearMonth.current(),
    today: LocalDate = today(),
    entries: Map<LocalDate, CalendarDayEntry> = emptyMap(),
    onMenuClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
) {
    // Int.MAX_VALUE 크기의 pager 로 사실상 무한 좌우 스와이프. 중간에서 시작해 양쪽으로 무제한 이동.
    val anchorPage = remember { Int.MAX_VALUE / 2 }
    val pagerState = rememberPagerState(
        initialPage = anchorPage,
        pageCount = { Int.MAX_VALUE },
    )
    // 현재 페이지가 앵커에서 몇 개월 오프셋인지 → 그만큼 이동한 YearMonth
    val currentYearMonth by remember(initialYearMonth) {
        derivedStateOf { initialYearMonth.plusMonths(pagerState.currentPage - anchorPage) }
    }
    // data.go.kr 특일정보 API 로 현재 보이는 연도의 공휴일 자동 로드. 실패해도 빈 맵.
    val holidayEntries = rememberHolidayEntries(currentYearMonth.year)
    val mergedEntries = remember(entries, holidayEntries) {
        mergeEntries(base = holidayEntries, override = entries)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
    ) {
        MainToolbar(
            yearMonth = currentYearMonth,
            onMenuClick = onMenuClick,
            onTitleClick = onTitleClick,
            onSearchClick = onSearchClick,
        )
        WeekdaysRow()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            val pageYearMonth = initialYearMonth.plusMonths(page - anchorPage)
            val cells = remember(pageYearMonth) { buildMonthCells(pageYearMonth) }
            CalendarGrid(
                cells = cells,
                today = today,
                entries = mergedEntries,
                onDayClick = onDayClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 두 entry 맵을 병합. 같은 날짜면 events 를 이어 붙이고 (base + override 순), lunarLabel 은
 * override 우선. 사용자 지정 entries (override) 가 API 공휴일 (base) 위에 얹히는 방향.
 */
private fun mergeEntries(
    base: Map<LocalDate, CalendarDayEntry>,
    override: Map<LocalDate, CalendarDayEntry>,
): Map<LocalDate, CalendarDayEntry> {
    if (override.isEmpty()) return base
    if (base.isEmpty()) return override
    val out = base.toMutableMap()
    for ((date, ov) in override) {
        val b = out[date]
        out[date] = if (b == null) ov else CalendarDayEntry(
            events = b.events + ov.events,
            lunarLabel = ov.lunarLabel ?: b.lunarLabel,
        )
    }
    return out
}

// ────────── Toolbar ──────────

@Composable
private fun MainToolbar(
    yearMonth: YearMonth,
    onMenuClick: () -> Unit,
    onTitleClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp) // 44dp 원형 탭 타겟이 좌우로 튀어나오지 않게 살짝 안쪽
            .padding(top = 8.dp, bottom = 12.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 좌: 햄버거 — 44dp 리퀴드 글래스 원형 버튼 안에 24dp 아이콘 (BackCircleButton 과 동일한 tint)
        IconTapTarget(onClick = onMenuClick) {
            Image(
                painter = painterResource(Res.drawable.ic_menu),
                contentDescription = "메뉴 열기",
                colorFilter = ColorFilter.tint(TextPrimary),
                modifier = Modifier.size(24.dp),
            )
        }

        // 중앙: "YYYY. M v" — rounded rect 리플
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTitleClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${yearMonth.year}. ${yearMonth.month}",
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = CalendarWeekdayText,
                ),
            )
            Image(
                painter = painterResource(Res.drawable.ic_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }

        // 우: 검색 — 햄버거/BackCircleButton 과 동일 톤 (tint TextPrimary)
        IconTapTarget(onClick = onSearchClick) {
            Image(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = "검색",
                colorFilter = ColorFilter.tint(TextPrimary),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 44dp 리퀴드 글래스 원형 버튼. `ui/common/AppTopBar.BackCircleButton` (ProfileSetupScreen 뒤로가기)
 * 과 동일한 모디파이어 구성으로 톤을 통일한다: `liquidGlass(CircleShape)` → `clip(CircleShape)` →
 * `clickable`. clip 이 clickable 앞이라 ripple 이 원형으로 잘림.
 */
@Composable
private fun IconTapTarget(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .liquidGlass(shape = CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ────────── Weekdays ──────────

private val WeekdayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
private fun WeekdaysRow() {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeekdayLabels.forEachIndexed { index, label ->
            val color = when (index) {
                0 -> CalendarSunday
                6 -> CalendarSaturday
                else -> CalendarWeekdayText
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = color,
                    ),
                )
            }
        }
    }
}

// ────────── Grid ──────────

/** 6주 × 7일 셀. 매월 항상 42칸으로 렌더해 높이 흔들림 방지. */
private data class MonthCell(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
)

private fun buildMonthCells(ym: YearMonth): List<MonthCell> {
    val firstOfMonth = LocalDate(ym.year, ym.month, 1)
    // 일요일 시작 그리드: 첫 주 앞에 채울 이전달 일수 = 일요일부터의 오프셋
    // DayOfWeek.ordinal: MONDAY=0..SUNDAY=6 → Sunday-based offset = (ordinal + 1) % 7
    val leading = (firstOfMonth.dayOfWeek.ordinal + 1) % 7
    val gridStart = firstOfMonth.minus(leading, DateTimeUnit.DAY)
    return List(42) { i ->
        val date = gridStart.plus(i, DateTimeUnit.DAY)
        MonthCell(
            date = date,
            isCurrentMonth = date.year == ym.year && date.month.ordinal + 1 == ym.month,
        )
    }
}

@Composable
private fun CalendarGrid(
    cells: List<MonthCell>,
    today: LocalDate,
    entries: Map<LocalDate, CalendarDayEntry>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 6 주
        for (week in 0 until 6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                for (dow in 0 until 7) {
                    val cell = cells[week * 7 + dow]
                    DayCell(
                        cell = cell,
                        isToday = cell.date == today,
                        entry = entries[cell.date],
                        onClick = { onDayClick(cell.date) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: MonthCell,
    isToday: Boolean,
    entry: CalendarDayEntry?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pretendard = LocalPretendardFontFamily.current
    val dayColor = dayNumberColor(cell)
    val sorted = remember(entry) {
        entry?.events?.sortedBy { it.type.priority }.orEmpty()
    }
    val visibleChips = sorted.take(2)
    val overflow = (sorted.size - visibleChips.size).coerceAtLeast(0)

    Column(
        modifier = modifier
            // 셀 전체 (숫자·chip 있든 없든) 를 리플 영역으로 잡기 위해 높이도 부모 (한 주 Row) 를 꽉 채움.
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(top = 10.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (isToday) {
            // 28dp 원 가운데 정렬은 폰트 ascent 만큼 시각 중심이 아래로 밀리기 때문에,
            // 옆 셀 평문 숫자와 line 이 맞도록 원을 살짝 위로 올린다.
            Box(
                modifier = Modifier
                    .offset(y = (-4).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CalendarTodayCircle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.date.day.toString(),
                    style = TextStyle(
                        fontFamily = pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CalendarTodayText,
                    ),
                )
            }
        } else {
            Text(
                text = cell.date.day.toString(),
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = dayColor,
                ),
            )
        }

        entry?.lunarLabel?.let { label ->
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Normal,
                    fontSize = 9.sp,
                    color = CalendarLunarText,
                ),
            )
        }

        visibleChips.forEach { event ->
            EventChip(event = event)
        }
        if (overflow > 0) {
            OverflowChip(count = overflow)
        }
    }
}

private fun dayNumberColor(cell: MonthCell): Color {
    val base = when (cell.date.dayOfWeek) {
        DayOfWeek.SUNDAY -> CalendarSunday
        DayOfWeek.SATURDAY -> CalendarSaturday
        else -> CalendarWeekdayText
    }
    return if (cell.isCurrentMonth) base else base.copy(alpha = 0.3f)
}

// ────────── Chips ──────────

@Composable
private fun EventChip(event: CalendarEvent) {
    val pretendard = LocalPretendardFontFamily.current
    val (bg, fg) = when (event.type) {
        CalendarEventType.Holiday -> ChipHolidayBg to ChipHolidayText
        CalendarEventType.Season -> ChipSeasonBg to ChipSeasonText
        CalendarEventType.Personal -> ChipPersonalBg to ChipPersonalText
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = event.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = fg,
            ),
        )
    }
}

@Composable
private fun OverflowChip(count: Int) {
    val pretendard = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(ChipSeasonBg)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            maxLines = 1,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = ChipSeasonText,
            ),
        )
    }
}

// ────────── Preview ──────────

@Preview
@Composable
private fun MainScreenPreview() {
    val ym = YearMonth(2026, 8)
    val today = LocalDate(2026, 8, 10)
    val entries = mapOf(
        LocalDate(2026, 8, 6) to CalendarDayEntry(
            events = listOf(CalendarEvent("위드헤어", CalendarEventType.Personal)),
        ),
        LocalDate(2026, 8, 7) to CalendarDayEntry(
            events = listOf(CalendarEvent("입추", CalendarEventType.Season)),
        ),
        LocalDate(2026, 8, 14) to CalendarDayEntry(
            events = listOf(CalendarEvent("말복", CalendarEventType.Season)),
        ),
        LocalDate(2026, 8, 15) to CalendarDayEntry(
            events = listOf(CalendarEvent("광복절", CalendarEventType.Holiday)),
        ),
        LocalDate(2026, 8, 17) to CalendarDayEntry(
            events = listOf(CalendarEvent("대체공휴일", CalendarEventType.Holiday)),
        ),
        LocalDate(2026, 8, 23) to CalendarDayEntry(
            events = listOf(CalendarEvent("처서", CalendarEventType.Season)),
        ),
        LocalDate(2026, 8, 27) to CalendarDayEntry(
            lunarLabel = "7.15",
        ),
    )
    ProvidePretendard {
        MainScreen(initialYearMonth = ym, today = today, entries = entries)
    }
}

@Preview
@Composable
private fun MainScreenOverflowPreview() {
    // 하루 3개 이상 이벤트 → 우선순위 상위 2개 chip + "+N"
    val ym = YearMonth(2026, 8)
    val today = LocalDate(2026, 8, 10)
    val entries = mapOf(
        LocalDate(2026, 8, 12) to CalendarDayEntry(
            events = listOf(
                CalendarEvent("개인약속", CalendarEventType.Personal),
                CalendarEvent("절기", CalendarEventType.Season),
                CalendarEvent("공휴일", CalendarEventType.Holiday),
                CalendarEvent("추가", CalendarEventType.Personal),
            ),
        ),
    )
    ProvidePretendard {
        MainScreen(initialYearMonth = ym, today = today, entries = entries)
    }
}
