package com.hyunjine.linker.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import com.hyunjine.linker.data.specialday.SpecialDayKind
import com.hyunjine.linker.designsystem.common.AppDrawer
import com.hyunjine.linker.designsystem.common.YearMonthPickerSheet
import com.hyunjine.linker.designsystem.common.liquidGlass
import com.hyunjine.linker.designsystem.theme.Background
import com.hyunjine.linker.designsystem.theme.CalendarLunarText
import com.hyunjine.linker.designsystem.theme.CalendarSaturday
import com.hyunjine.linker.designsystem.theme.CalendarSunday
import com.hyunjine.linker.designsystem.theme.CalendarTodayCircle
import com.hyunjine.linker.designsystem.theme.CalendarTodayText
import com.hyunjine.linker.designsystem.theme.CalendarWeekdayText
import com.hyunjine.linker.designsystem.theme.ChipHolidayBg
import com.hyunjine.linker.designsystem.theme.ChipHolidayText
import com.hyunjine.linker.designsystem.theme.ChipPersonalBg
import com.hyunjine.linker.designsystem.theme.ChipPersonalText
import com.hyunjine.linker.designsystem.theme.ChipSeasonBg
import com.hyunjine.linker.designsystem.theme.ChipSeasonText
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
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
    /**
     * 스케줄 chip 의 커스텀 색상 (owner 별로 다름). null 이면 [type] 의 기본 팔레트 사용.
     * bg 는 이 색의 옅은 tint (alpha 0.2), fg 는 이 색 자체.
     */
    val tintColor: Color? = null,
    /**
     * 원본 데이터 식별자. 스케줄 row 는 UUID, Holiday/Season 처럼 외부 API 산 chip 은 null.
     * 월별 캐시 (fetch 범위 ±7일 padding) 가 겹치면 같은 스케줄이 여러 달 버킷에 담기는데,
     * 병합 시 이 id 로 dedupe 해서 하나만 남긴다.
     */
    val id: String? = null,
    /**
     * 스케줄 chip 의 소유자 (뷰어 관점). 드로워의 "내 캘린더" · "상대방 캘린더" 토글로 필터링.
     * Holiday · Season 처럼 소유자 개념이 없는 chip 은 null.
     */
    val owner: DayOwner? = null,
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
 * @param onAddSchedule 그리드 셀 롱프레스 (iOS 캘린더 관습) — 해당 날짜를 기준으로 일정 생성 화면 진입.
 */
@Composable
fun MainScreen(
    initialYearMonth: YearMonth = YearMonth.current(),
    today: LocalDate = today(),
    /**
     * Preview · 테스트용 정적 override. 실제 앱에서는 [onLoadEntriesForMonth] 로 월별 로드된
     * entries 와 병합된다.
     */
    entries: Map<LocalDate, CalendarDayEntry> = emptyMap(),
    /**
     * ViewModel 이 유지하는 월별 chip 캐시. 화면은 여기서 읽기만 하고, 없는 달은 [onMonthVisible]
     * 콜백으로 알림.
     */
    entriesByMonth: Map<YearMonth, Map<LocalDate, CalendarDayEntry>> = emptyMap(),
    /** 새 달이 화면에 노출됐음을 상위에 알림. 캐시 미스면 상위가 lazy 로 fetch 트리거. */
    onMonthVisible: (YearMonth) -> Unit = {},
    /** 선택 날짜의 상세 payload 를 조회. null 이면 sheet 를 안 띄운다. */
    onLoadDayDetail: suspend (LocalDate) -> DayDetail? = { null },
    /** Task 체크박스 토글 (id, 새 값). 실패는 상위에서 처리 · 상위 로직 없이 옵티미스틱 반영. */
    onToggleTaskDone: suspend (id: String, done: Boolean) -> Unit = { _, _ -> },
    onMenuClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    /**
     * 일정 생성 진입. 롱프레스 · DayDetailSheet "+" 두 경로가 공유. [type] 은 어느 pill/롱프레스인지에
     * 따라 다름 — 롱프레스는 [ScheduleType.Schedule] 기본, "+" pill 은 사용자가 고른 것.
     */
    onAddSchedule: (LocalDate, com.hyunjine.linker.feature.schedule.ScheduleType) -> Unit = { _, _ -> },
    onEditSchedule: (id: String) -> Unit = {},
    onAnniversaryClick: () -> Unit = {},
    onProfileEditClick: () -> Unit = {},
    onCoupleLinkClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    /** 드로워 프로필 헤더에 표시할 값들. 로드 전에는 기본값 표시. */
    profileName: String = "",
    profileHandle: String = "",
    profileImageUrl: String? = null,
    /**
     * 드로워 표시 옵션 (일정/달력 정보). 상위 (VM) 가 서버에서 로드해 관리.
     * Preview 등 상위가 없는 컨텍스트에서는 기본값으로 렌더.
     */
    displayState: DrawerDisplayState = DrawerDisplayState(),
    /** 드로워 토글 변경 콜백. VM 이 옵티미스틱 반영 + 서버 upsert. */
    onDisplayStateChange: (DrawerDisplayState) -> Unit = {},
    /** 파트너 조인 여부. 드로워의 "상대방 캘린더" 토글 노출 · 스케줄 필터에 사용. */
    hasPartner: Boolean = false,
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
    // data.go.kr 특일정보 API 로 현재 보이는 연도의 공휴일 + 24절기 자동 로드. 실패해도 빈 맵.
    // 항상 둘 다 fetch 해서 캐시에 담아두고, 표시 여부는 아래 filter 로 즉시 반영 (토글 시 네트워크 대기 없음).
    val specialDayEntries = rememberSpecialDayEntries(
        year = currentYearMonth.year,
        SpecialDayKind.Holiday,
        SpecialDayKind.SolarTerm,
    )
    // 타이틀 탭 시 년/월 피커 시트 오픈. dismiss 시 선택 값으로 pager 를 해당 월까지 스크롤.
    var pickerVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 셀 탭 시 날짜 상세 시트 오픈.
    //  - `selectedDateString` · `sheetVisible` 둘 다 rememberSaveable. peek 로 재composition 돼도
    //    이전 상태 그대로 복원 · LaunchedEffect 에 의한 자동 재오픈 없음 (#156).
    //  - dayDetail 은 sheetVisible=true 로 전환될 때만 fetch.
    //  - chip 탭으로 편집 화면 진입 시엔 sheetVisible=false 로 명시 dismiss → 저장 상태도 false 라
    //    편집 pop 후에도 시트 안 뜸. 사용자가 원하면 셀 다시 탭.
    var selectedDateString by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = remember(selectedDateString) { selectedDateString?.let { LocalDate.parse(it) } }
    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    var dayDetail by remember(selectedDate) { mutableStateOf<DayDetail?>(null) }
    LaunchedEffect(selectedDate, sheetVisible) {
        if (sheetVisible && selectedDate != null && dayDetail == null) {
            dayDetail = onLoadDayDetail(selectedDate)
        }
    }
    // 사이드 드로워 표시 옵션은 상위 (VM) 가 소유 · 서버에 영구 저장.
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 현재 보이는 달이 바뀌면 상위에 알림 → ViewModel 이 캐시에 없으면 lazy fetch.
    LaunchedEffect(currentYearMonth) { onMonthVisible(currentYearMonth) }
    val scheduleEntries = remember(entriesByMonth) {
        entriesByMonth.values.fold(emptyMap<LocalDate, CalendarDayEntry>()) { acc, next ->
            mergeEntries(base = acc, override = next)
        }
    }

    // 표시 토글 반영: 공휴일/절기 는 type 기준, 개인 chip 은 owner 기준 (나/상대방/공동) 으로 각각 필터.
    val mergedEntries = remember(
        entries, scheduleEntries, specialDayEntries,
        displayState.showHolidays, displayState.showSolarTerms,
        displayState.showMyCalendar, displayState.showPartnerCalendar, displayState.showSharedCalendar,
    ) {
        val filteredSpecial = specialDayEntries.filterByToggles(
            showHolidays = displayState.showHolidays,
            showSolarTerms = displayState.showSolarTerms,
        )
        val filteredSchedules = scheduleEntries.filterByOwnerToggles(
            showMy = displayState.showMyCalendar,
            showPartner = displayState.showPartnerCalendar,
            showShared = displayState.showSharedCalendar,
        )
        val withSchedules = mergeEntries(base = filteredSpecial, override = filteredSchedules)
        mergeEntries(base = withSchedules, override = entries)
    }

    AppDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                profileName = profileName,
                profileHandle = profileHandle,
                profileImageUrl = profileImageUrl,
                displayState = displayState,
                hasPartner = hasPartner,
                onCoupleLinkClick = {
                    scope.launch { drawerState.close() }
                    onCoupleLinkClick()
                },
                onSettingsClick = {
                    // 여기서 drawerState.close() 를 부르면 App-scope 저장 상태가 Closed 로
                    // 굳어져서, 프로필 편집 후 돌아왔을 때 드로워가 다시 열리지 않는다.
                    // 편집 화면이 full-screen 이라 드로워는 자연스럽게 가려지므로 닫을 필요 X.
                    onProfileEditClick()
                },
                onAnniversaryClick = {
                    scope.launch { drawerState.close() }
                    onAnniversaryClick()
                },
                onToggleMyCalendar = { onDisplayStateChange(displayState.copy(showMyCalendar = it)) },
                onTogglePartnerCalendar = { onDisplayStateChange(displayState.copy(showPartnerCalendar = it)) },
                onToggleSharedCalendar = { onDisplayStateChange(displayState.copy(showSharedCalendar = it)) },
                onToggleHolidays = { onDisplayStateChange(displayState.copy(showHolidays = it)) },
                onToggleSolarTerms = { onDisplayStateChange(displayState.copy(showSolarTerms = it)) },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
        },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            // 상태바/내비게이션바 padding 만 유지. `safeDrawing` 은 IME · 일부 인셋이 상황에 따라
            // 변동하는데, `systemBars` 는 IME/포커스 상태와 무관하게 정적이라 캘린더 튐 완전 해소.
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
    ) {
        MainToolbar(
            yearMonth = currentYearMonth,
            onMenuClick = {
                onMenuClick()
                scope.launch { drawerState.open() }
            },
            onTitleClick = {
                onTitleClick()
                pickerVisible = true
            },
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
                onDayClick = { date ->
                    onDayClick(date)
                    selectedDateString = date.toString()
                    sheetVisible = true
                },
                onDayLongClick = { date ->
                    onAddSchedule(date, com.hyunjine.linker.feature.schedule.ScheduleType.Schedule)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // 캘린더는 오늘 기준 ±100년 이동 가능. 월은 각 연도 1~12 전체 허용.
    YearMonthPickerSheet(
        visible = pickerVisible,
        date = LocalDate(currentYearMonth.year, currentYearMonth.month, 1),
        minDate = LocalDate(today.year - 100, 1, 1),
        maxDate = LocalDate(today.year + 100, 12, 1),
        onConfirm = { picked ->
            pickerVisible = false
            val target = YearMonth(picked.year, picked.month.ordinal + 1)
            val delta = target.monthsSince(initialYearMonth)
            scope.launch { pagerState.scrollToPage(anchorPage + delta) }
        },
        onCancel = { pickerVisible = false },
    )

    // 셀 탭 → 날짜 상세 시트.
    DayDetailSheet(
        visible = sheetVisible && dayDetail != null,
        detail = dayDetail,
        onDismiss = {
            sheetVisible = false
            selectedDateString = null
        },
        onToggleTask = { taskId ->
            val current = dayDetail ?: return@DayDetailSheet
            val target = current.tasks.find { it.id == taskId } ?: return@DayDetailSheet
            val newValue = !target.isDone
            // 옵티미스틱: 즉시 UI 반영 후 서버 저장. 실패는 로그만 (재조회 시 서버 값으로 복원).
            dayDetail = current.copy(
                tasks = current.tasks.map { if (it.id == taskId) it.copy(isDone = newValue) else it },
            )
            scope.launch { runCatching { onToggleTaskDone(taskId, newValue) } }
        },
        onAdd = { type ->
            // 시트 안 pill (할 일 / 일정) 탭 → 일정 생성 진입.
            //  - sheetVisible = false 로 즉시 dismiss (slide-down 애니 없이 편집 화면이 올라옴).
            //  - selectedDateString 은 유지하되 sheetVisible=false 는 saveable 로 저장돼
            //    pop 후에도 시트 안 뜸 (#156 · peek 재오픈 방지). 원하면 사용자가 셀 다시 탭.
            val date = selectedDate ?: return@DayDetailSheet
            sheetVisible = false
            onAddSchedule(date, type)
        },
        onSelectSchedule = { scheduleId ->
            sheetVisible = false
            onEditSchedule(scheduleId)
        },
        onSelectTask = { taskId ->
            // 체크박스 외 영역 탭 → 편집 화면 진입 (일정과 동일 흐름). 체크박스는 자체 clickable 로 토글.
            sheetVisible = false
            onEditSchedule(taskId)
        },
    )
    } // ← AppDrawer content lambda close
}

/** [other] 로부터 이 [YearMonth] 까지 몇 개월 뒤인지. `other.plusMonths(this.monthsSince(other)) == this`. */
private fun YearMonth.monthsSince(other: YearMonth): Int =
    (year - other.year) * 12 + (month - other.month)

/**
 * 드로워의 표시 옵션에 따라 API-derived entries 에서 chip 을 걸러낸다.
 * 남는 이벤트가 없어지고 [CalendarDayEntry.lunarLabel] 도 없다면 해당 날짜는 맵에서 제거해
 * 셀 렌더링에서 무의미한 조회를 없앤다.
 */
private fun Map<LocalDate, CalendarDayEntry>.filterByToggles(
    showHolidays: Boolean,
    showSolarTerms: Boolean,
): Map<LocalDate, CalendarDayEntry> {
    if (showHolidays && showSolarTerms) return this
    val out = mutableMapOf<LocalDate, CalendarDayEntry>()
    for ((date, entry) in this) {
        val kept = entry.events.filter { ev ->
            when (ev.type) {
                CalendarEventType.Holiday -> showHolidays
                CalendarEventType.Season -> showSolarTerms
                CalendarEventType.Personal -> true
            }
        }
        if (kept.isNotEmpty() || entry.lunarLabel != null) {
            out[date] = entry.copy(events = kept)
        }
    }
    return out
}

/**
 * 스케줄 chip 을 드로워 "내 캘린더" · "상대방 캘린더" · "공동 캘린더" 토글로 걸러낸다.
 * 세 토글은 서로 독립적이라 owner 별로 대응되는 스위치만 본다.
 * `owner=null` 은 방어적으로 통과 (스케줄 chip 은 모두 owner 를 갖는 게 정상).
 */
private fun Map<LocalDate, CalendarDayEntry>.filterByOwnerToggles(
    showMy: Boolean,
    showPartner: Boolean,
    showShared: Boolean,
): Map<LocalDate, CalendarDayEntry> {
    if (showMy && showPartner && showShared) return this
    val out = mutableMapOf<LocalDate, CalendarDayEntry>()
    for ((date, entry) in this) {
        val kept = entry.events.filter { ev ->
            when (ev.owner) {
                DayOwner.Me -> showMy
                DayOwner.Partner -> showPartner
                DayOwner.Us -> showShared
                null -> true
            }
        }
        if (kept.isNotEmpty() || entry.lunarLabel != null) {
            out[date] = entry.copy(events = kept)
        }
    }
    return out
}

/**
 * 두 entry 맵을 병합. 같은 날짜면 events 를 이어 붙이고 (base + override 순), lunarLabel 은
 * override 우선. 사용자 지정 entries (override) 가 API 공휴일 (base) 위에 얹히는 방향.
 *
 * 월별 캐시 병합 시 fetch 범위 ±7일 padding 때문에 같은 스케줄이 인접 두 달 버킷에 모두 담기는
 * 경우가 있어, [CalendarEvent.id] 로 dedupe (id 있는 것만) — Holiday/Season 처럼 id 가 null 인
 * chip 은 원래 겹칠 소스가 아니라 그대로 통과.
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
            events = (b.events + ov.events).distinctById(),
            lunarLabel = ov.lunarLabel ?: b.lunarLabel,
        )
    }
    return out
}

/** id 있는 chip 은 유일하게, id 없는 chip 은 순서 유지하며 그대로 통과. */
private fun List<CalendarEvent>.distinctById(): List<CalendarEvent> {
    val seen = mutableSetOf<String>()
    return buildList {
        for (event in this@distinctById) {
            if (event.id == null || seen.add(event.id)) add(event)
        }
    }
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
    onDayLongClick: (LocalDate) -> Unit,
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
                        onLongClick = { onDayLongClick(cell.date) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    cell: MonthCell,
    isToday: Boolean,
    entry: CalendarDayEntry?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(top = 10.dp)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // 모든 셀의 숫자 컨테이너를 28dp Box 로 통일 → 오늘/평일 모두 같은 baseline.
        // 오늘 셀만 원 배경을 얹고 텍스트 색만 반전 (offset · fontSize 변주 없이 정확히 겹침).
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) Modifier.clip(CircleShape).background(CalendarTodayCircle)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.date.day.toString(),
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = if (isToday) CalendarTodayText else dayColor,
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
    val (bg, fg) = when {
        event.tintColor != null -> event.tintColor.copy(alpha = 0.18f) to event.tintColor
        event.type == CalendarEventType.Holiday -> ChipHolidayBg to ChipHolidayText
        event.type == CalendarEventType.Season -> ChipSeasonBg to ChipSeasonText
        else -> ChipPersonalBg to ChipPersonalText
    }
    // API 가 "대체공휴일(광복절)" 처럼 괄호로 원출처를 덧붙여 보내는 케이스 → 셀 폭이 좁으니 chip 에는
    // 괄호 앞까지만 노출. 원본은 [CalendarEvent.label] 에 그대로 유지 (나중에 상세 화면용).
    val display = event.label.substringBefore('(').trim()
    ChipText(text = display, bg = bg, fg = fg)
}

@Composable
private fun OverflowChip(count: Int) {
    ChipText(text = "+$count", bg = ChipSeasonBg, fg = ChipSeasonText)
}

/**
 * chip 공통 렌더. Box 래퍼를 벗겨 배경·클립·패딩을 Text 에 직접 얹었다.
 * 셀 가로 폭을 꽉 채우려면 [Modifier.fillMaxWidth] 가 필요하고, 그 안에서 가운데 정렬은
 * [TextAlign.Center]. `maxLines=1 + Ellipsis` 로 좁은 셀에서도 넘치지 않게.
 */
@Composable
private fun ChipText(text: String, bg: Color, fg: Color) {
    val pretendard = LocalPretendardFontFamily.current
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        style = TextStyle(
            fontFamily = pretendard,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = fg,
        ),
    )
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
