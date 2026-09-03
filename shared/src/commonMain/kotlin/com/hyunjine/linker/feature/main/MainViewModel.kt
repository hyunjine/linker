package com.hyunjine.linker.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.local.DrawerDisplayLocal
import com.hyunjine.linker.data.remote.CoupleRealtimeSubscription
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.data.remote.subscribeCoupleRealtime
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import com.hyunjine.linker.platform.refreshTodayWidget
import kotlinx.coroutines.Job
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
     * 드로워 표시 옵션. 로컬 저장소 (SharedPreferences · NSUserDefaults) 에서 sync 로드 →
     * 앱 프로세스 재시작 후에도 유지. 서버 저장 방식은 화면 이탈 시 in-flight HTTP 유실로
     * "죄다 off" 되던 버그가 있어 로컬로 전환 (#167).
     */
    private val _drawerDisplay = MutableStateFlow(DrawerDisplayLocal.load())
    val drawerDisplay: StateFlow<DrawerDisplayState> = _drawerDisplay.asStateFlow()

    /** 드로워 토글 반영. 로컬 저장은 sync 라 fire-and-forget · scope 고민 없음. */
    fun updateDrawerDisplay(next: DrawerDisplayState) {
        _drawerDisplay.value = next
        DrawerDisplayLocal.save(next)
    }

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
            val partnerProfile = runCatching { UsersRepository.partnerProfile() }.getOrNull()
            val partnerColor = partnerProfile?.calendarColor
            val nextColors = OwnerColors(
                me = calendarColorFor(mine?.calendarColor),
                partner = calendarColorFor(partnerColor ?: "pink"),
                us = CalendarPurple,
            )
            val previousColors = _uiState.value.ownerColors
            val previousViewerId = _uiState.value.myProfile?.id
            val previousMonths = _uiState.value.entriesByMonth.keys.toSet()
            // 캐시를 비우지 않는다 — 이전 tint 로 살아 있는 chip 을 유지하다가 도착 순서대로 atomic 교체.
            // (예전엔 emptyMap 으로 비우고 다시 채우느라 chip 이 순간 사라졌다 다시 뜨는 깜빡임이 있었음 #141)
            _uiState.update {
                it.copy(
                    myProfile = mine,
                    ownerColors = nextColors,
                    hasPartner = partnerProfile != null,
                )
            }
            val needsReload = previousColors != nextColors || (previousViewerId == null && mine?.id != null)
            if (needsReload) {
                previousMonths.forEach { fetchAndReplaceMonth(it) }
            }
        }
    }

    /** 해당 달의 chip 이 캐시에 없으면 fetch 해 채운다. 이미 있으면 no-op. */
    fun loadMonthIfNeeded(yearMonth: YearMonth) {
        if (_uiState.value.entriesByMonth.containsKey(yearMonth)) return
        fetchAndReplaceMonth(yearMonth)
    }

    /**
     * 강제로 다시 fetch 해서 도착한 즉시 해당 달 entry 를 atomic 하게 교체.
     * 캐시를 미리 비우지 않아 사용자 화면에서 chip 이 "잠깐 사라졌다 다시 뜨는" 깜빡임 없음.
     */
    private fun fetchAndReplaceMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val first = LocalDate(yearMonth.year, yearMonth.month, 1)
            val from = first.plus(-7, DateTimeUnit.DAY)
            val to = first.plus(1, DateTimeUnit.MONTH).plus(7, DateTimeUnit.DAY)
            val rows = runCatching { SchedulesRepository.listInRange(from, to) }
                .onFailure { println("[Schedule] listInRange($yearMonth) 실패: $it") }
                .getOrDefault(emptyList())
            val fetched = rows.toCalendarEntries(
                ownerColors = _uiState.value.ownerColors,
                viewerId = _uiState.value.myProfile?.id,
            )
            _uiState.update { it.copy(entriesByMonth = it.entriesByMonth + (yearMonth to fetched)) }
        }
    }

    /** 특정 날짜의 상세 payload (task · timed · all-day 분리) 로드. */
    suspend fun loadDayDetail(date: LocalDate): DayDetail {
        val rows = runCatching { SchedulesRepository.listInRange(date, date) }
            .onFailure { println("[Schedule] loadDayDetail($date) 실패: $it") }
            .getOrDefault(emptyList())
        return rows.toDayDetail(date, viewerId = _uiState.value.myProfile?.id)
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
     * 스케줄 CRUD 직후 · Realtime 이벤트 시 호출. 이미 로드된 달만 골라 백그라운드에서 재fetch 하고
     * 도착한 순서대로 해당 달 entry 를 atomic 하게 교체한다. **캐시를 미리 비우지 않는 것이 포인트** —
     * 이전 버전은 emptyMap 으로 비웠다가 다시 채우느라 화면 chip 이 순간 사라졌다 다시 뜨는 깜빡임이 있었음 (#141).
     */
    fun refreshSchedules() {
        _uiState.value.entriesByMonth.keys.toSet().forEach { fetchAndReplaceMonth(it) }
        // 파트너 CRUD · realtime 변경도 시작 시각 로컬 알림에 반영.
        viewModelScope.launch {
            runCatching { com.hyunjine.linker.feature.reminder.ReminderScheduler.rebuild() }
                .onFailure { println("[Reminder] rebuild after refresh 실패: $it") }
        }
    }

    // ────────── Realtime ──────────

    private var realtimeJob: Job? = null

    /**
     * Supabase Realtime 구독 시작. schedules · anniversaries · users · couple_members 변경을
     * 실시간 수신해 관련 UI 를 즉시 refresh. 이미 활성 구독이 있으면 no-op.
     * MainRoute 진입 시 · 커플 join 성공 후 (couple_id 바뀜) 두 시점에 호출.
     */
    fun startRealtime() {
        if (realtimeJob?.isActive == true) return
        realtimeJob = viewModelScope.subscribeCoupleRealtime(
            CoupleRealtimeSubscription(
                onSchedulesChanged = {
                    refreshSchedules()
                    // 파트너 변경도 오늘 위젯에 반영 (foreground 상태만 실제 fire).
                    viewModelScope.launch { refreshTodayWidget() }
                },
                onPartnerProfileChanged = { refreshProfile() },
                onCoupleChanged = { refreshProfile() },
            ),
        )
    }

    /** couple 이 바뀌면 (join 성공 후) 채널을 새 couple_id 로 다시 만들어야 함. */
    fun restartRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        startRealtime()
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}

/** MainScreen 이 소비하는 화면 상태. */
data class MainUiState(
    val myProfile: com.hyunjine.linker.data.remote.UsersRepository.Profile? = null,
    val ownerColors: OwnerColors = OwnerColors.Default,
    val entriesByMonth: Map<YearMonth, Map<LocalDate, CalendarDayEntry>> = emptyMap(),
    /** 파트너 조인 여부. 드로워의 "상대방 캘린더" 토글 노출 · 캘린더 필터에 사용. */
    val hasPartner: Boolean = false,
)
