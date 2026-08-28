package com.hyunjine.linker.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import com.hyunjine.linker.platform.refreshTodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * MainScreen 의 state · 도메인 로직. Nav 진입/이탈에 살아남아 검색 왕복 시 chip 재fetch 로 인한
 * 깜빡임을 원천 방지한다.
 *
 * - [uiState] 는 unidirectional. UI 는 `collectAsStateWithLifecycle` 로 구독하고 이벤트만 콜백으로 전달.
 * - 프로필 · 파트너 색은 [refreshProfile] 로 최초/편집 후 다시 로드.
 * - 월별 chip 캐시는 [uiState.entriesByMonth] 에 담고 [loadMonthIfNeeded] 로 lazy 채움.
 * - [ownerColors] 가 바뀌면 캐시를 통째로 비워 새 색으로 다시 tint (프로필 색 변경 대응).
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * 앱 진입 · 프로필 편집 후에 호출. 내 · 파트너 프로필을 다시 조회하고 owner 색이 바뀌었으면
     * 기존 chip 캐시를 무효화한 뒤 즉시 재fetch — 앱 시작 시 [loadMonthIfNeeded] 와의 race 로
     * chip 이 사라지는 문제를 방지 (프로필 fetch 가 늦게 끝나면 default 색으로 채워진 chip 을
     * 통째로 지우기 때문. `onMonthVisible` 은 재fire 안 되니 스스로 다시 요청해야 함).
     */
    fun refreshProfile() {
        viewModelScope.launch {
            val mine = runCatching { UsersRepository.myProfile() }
                .onFailure { println("[Main] myProfile 실패: $it") }
                .getOrNull()
            val partner = runCatching { UsersRepository.partnerProfile()?.calendarColor }.getOrNull()
            val nextColors = OwnerColors(
                me = calendarColorFor(mine?.calendarColor),
                partner = calendarColorFor(partner ?: "pink"),
                us = CalendarPurple,
            )
            val previousColors = _uiState.value.ownerColors
            val previousMonths = _uiState.value.entriesByMonth.keys.toSet()
            _uiState.update {
                val cacheInvalidated = it.ownerColors != nextColors
                it.copy(
                    myProfile = mine,
                    ownerColors = nextColors,
                    entriesByMonth = if (cacheInvalidated) emptyMap() else it.entriesByMonth,
                )
            }
            if (previousColors != nextColors) {
                previousMonths.forEach { loadMonthIfNeeded(it) }
            }
        }
    }

    /** 해당 달의 chip 이 캐시에 없으면 fetch 해 채운다. 이미 있으면 no-op. */
    fun loadMonthIfNeeded(yearMonth: YearMonth) {
        if (_uiState.value.entriesByMonth.containsKey(yearMonth)) return
        viewModelScope.launch {
            val first = LocalDate(yearMonth.year, yearMonth.month, 1)
            val from = first.plus(-7, DateTimeUnit.DAY)
            val to = first.plus(1, DateTimeUnit.MONTH).plus(7, DateTimeUnit.DAY)
            val rows = runCatching { SchedulesRepository.listInRange(from, to) }
                .onFailure { println("[Schedule] listInRange($yearMonth) 실패: $it") }
                .getOrDefault(emptyList())
            val fetched = rows.toCalendarEntries(_uiState.value.ownerColors)
            _uiState.update { it.copy(entriesByMonth = it.entriesByMonth + (yearMonth to fetched)) }
        }
    }

    /** 특정 날짜의 상세 payload (task · timed · all-day 분리) 로드. */
    suspend fun loadDayDetail(date: LocalDate): DayDetail {
        val rows = runCatching { SchedulesRepository.listInRange(date, date) }
            .onFailure { println("[Schedule] loadDayDetail($date) 실패: $it") }
            .getOrDefault(emptyList())
        return rows.toDayDetail(date)
    }

    /** 할 일 체크박스 토글. 실패는 삼키고 로그만 남김 (옵티미스틱 UI 는 UI 층에서 별도 처리). */
    suspend fun toggleTaskDone(id: String, done: Boolean) {
        runCatching { SchedulesRepository.setTaskDone(id, done) }
            .onSuccess { refreshTodayWidget() }
            .onFailure { println("[Schedule] setTaskDone 실패: $it") }
    }

    /** 프로필 편집 · 스케줄 편집 등으로 캐시가 stale 됐을 때 호출. 다음 progressive 로드에서 다시 채움. */
    fun invalidateEntriesCache() {
        _uiState.update { it.copy(entriesByMonth = emptyMap()) }
    }

    /**
     * 스케줄 CRUD 직후 호출. 이미 로드된 달들을 기억해 두었다가 캐시를 비우고 다시 fetch —
     * 화면에 보이는 달은 pager 재구성 없이도 즉시 최신 chip 으로 갱신된다.
     * (invalidateEntriesCache 만 부르면 onMonthVisible 이 다시 안 불려 새로 뜰 때까지 chip 이 안 보임.)
     */
    fun refreshSchedules() {
        val monthsToReload = _uiState.value.entriesByMonth.keys.toSet()
        _uiState.update { it.copy(entriesByMonth = emptyMap()) }
        monthsToReload.forEach { loadMonthIfNeeded(it) }
    }
}

/** MainScreen 이 소비하는 화면 상태. */
data class MainUiState(
    val myProfile: com.hyunjine.linker.data.remote.UsersRepository.Profile? = null,
    val ownerColors: OwnerColors = OwnerColors.Default,
    val entriesByMonth: Map<YearMonth, Map<LocalDate, CalendarDayEntry>> = emptyMap(),
)
