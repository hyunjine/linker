package com.hyunjine.linker.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.local.DrawerDisplayLocal
import com.hyunjine.linker.data.remote.CoupleRealtimeSubscription
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.SessionReadiness
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.data.remote.awaitUsableSession
import com.hyunjine.linker.data.remote.resolveUsCalendarColorId
import com.hyunjine.linker.data.remote.subscribeCoupleRealtime
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import com.hyunjine.linker.platform.refreshTodayWidget
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * MainScreen 의 state · 도메인 로직. Nav 진입/이탈에 살아남아 검색 왕복 시 chip 재fetch 로 인한
 * 깜빡임을 원천 방지한다.
 *
 * - [uiState] 는 unidirectional. UI 는 `collectAsStateWithLifecycle` 로 구독하고 이벤트만 콜백으로 전달.
 * - 프로필 · 파트너 색은 [refreshProfile] 로 최초/편집 후 다시 로드.
 * - 월별 chip 캐시는 [uiState.entriesByMonth] 에 담고 [loadMonthIfNeeded] 로 lazy 채움.
 * - [ownerColors] 가 바뀌면 캐시를 통째로 비워 새 색으로 다시 tint (프로필 색 변경 대응).
 */
/** 재진입 시 이미 캐시된 달 재조회 · 프로필 갱신의 최소 간격 (#422). 캐시에 없는 달은 throttle 없이 받는다. */
private val ResumeRefreshInterval = 30.seconds

/** 이 시간 안에 만료될 토큰은 조회 전에 갱신 (#422). */
private val SessionExpiryMargin = 30.seconds

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
            // 세션이 없을 때 조회하면 "프로필 없음 (null)" 이 성공으로 돌아와 기존 프로필 · 색을 지운다 (#422).
            // 쓸 수 있는 세션이 확보될 때까지 기다리고, 안 되면 상태를 건드리지 않는다 — 세션 복구 시 다시 호출됨.
            if (awaitUsableSession(expiryMargin = SessionExpiryMargin, logTag = "Main") != SessionReadiness.Ready) {
                println("[Main] 세션 미준비 — 프로필 갱신 건너뜀")
                return@launch
            }
            val mine = runCatching { UsersRepository.myProfile() }
                .onFailure { println("[Main] myProfile 실패: $it") }
                .getOrNull()
            val partnerProfile = runCatching { UsersRepository.partnerProfile() }.getOrNull()
            val partnerColor = partnerProfile?.calendarColor
            // 공동(Us) 색은 커플 공유값 (#392). 조회 실패해도 내 프로필 값으로 fallback 되게 null 처리.
            val couple = runCatching { CouplesRepository.myCoupleOrNull() }
                .onFailure { println("[Main] myCouple 실패: $it") }
                .getOrNull()
            if (SupabaseProvider.client.auth.currentSessionOrNull() == null) {
                println("[Main] 조회 중 세션 유실 — 프로필 갱신 결과 버림")
                return@launch
            }
            val nextColors = OwnerColors(
                me = calendarColorFor(mine?.calendarColor),
                partner = calendarColorFor(partnerColor ?: "pink"),
                // 공동(Us) 색: 커플 공유값 → 내 프로필 값 (#245) → CalendarPurple 순 fallback.
                us = resolveUsCalendarColorId(couple?.usCalendarColor, mine?.usCalendarColor)
                    ?.let { calendarColorFor(it) } ?: CalendarPurple,
            )
            val previousColors = _uiState.value.ownerColors
            val previousViewerId = _uiState.value.myProfile?.id
            val previousMonths = _uiState.value.entriesByMonth.keys.toSet()
            // 캐시를 비우지 않는다 — 이전 tint 로 살아 있는 chip 을 유지하다가 도착 순서대로 atomic 교체.
            // (예전엔 emptyMap 으로 비우고 다시 채우느라 chip 이 순간 사라졌다 다시 뜨는 깜빡임이 있었음 #141)
            _uiState.update {
                it.copy(
                    myProfile = mine,
                    partnerProfile = partnerProfile,
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

    /** 화면에 한 번이라도 요청된 달. 조회가 실패해 캐시에 없는 달도 재진입 때 다시 받기 위해 기억 (#422). */
    private val requestedMonths = mutableSetOf<YearMonth>()

    /**
     * 지금 조회 중인 달 → 그 조회 [Job]. 평소엔 같은 달 중복 요청을 걸러내고, 재진입 · 세션 복구 때는
     * 끊긴 연결에 매달린 stale 조회를 cancel 하고 새로 시작하는 데 쓴다 (#422).
     */
    private val inFlightMonths = mutableMapOf<YearMonth, Job>()

    /**
     * 마지막 재진입 갱신 시각. null = 아직 재진입 갱신 전 (첫 resume 은 진입 초기 로드와 겹치므로 캐시된 달 재조회는
     * 생략). 예전엔 VM 생성 시각으로 시작해 생성 직후 30초 동안 실패한 달 재조회까지 막혔다 (#422).
     */
    private var lastResumeRefresh: TimeSource.Monotonic.ValueTimeMark? = null

    init {
        observeSessionRecovery()
    }

    /** 해당 달의 chip 이 캐시에 없으면 fetch 해 채운다. 이미 있으면 no-op. */
    fun loadMonthIfNeeded(yearMonth: YearMonth) {
        requestedMonths += yearMonth
        if (_uiState.value.entriesByMonth.containsKey(yearMonth)) return
        fetchAndReplaceMonth(yearMonth)
    }

    /**
     * 강제로 다시 fetch 해서 도착한 즉시 해당 달 entry 를 atomic 하게 교체.
     * 캐시를 미리 비우지 않아 사용자 화면에서 chip 이 "잠깐 사라졌다 다시 뜨는" 깜빡임 없음.
     *
     * 실패 처리 (#422): 백그라운드에서 막 돌아온 직후엔 iOS 가 끊어 둔 연결 · 만료 토큰 때문에 요청이 자주
     * 실패한다. [fetchRangeWithRetry] 가 세션 확보 후 backoff 재시도하고, 그래도 실패하면 **캐시를 건드리지 않는다**
     * — 예전엔 빈 목록을 저장해 그 달이 계속 빈 채로 남거나, 떠 있던 일정이 빈 값으로 덮어써졌다.
     *
     * @param yearMonth 조회할 달.
     * @param force true 면 이미 조회 중인 같은 달 요청을 cancel 하고 새로 시작 (재진입 · 세션 복구 — 끊긴 연결에
     *   매달린 요청이 막지 못하게). false 면 조회 중일 때 no-op.
     */
    private fun fetchAndReplaceMonth(yearMonth: YearMonth, force: Boolean = false) {
        val running = inFlightMonths[yearMonth]
        if (running?.isActive == true) {
            if (!force) return
            running.cancel()
        }
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val first = LocalDate(yearMonth.year, yearMonth.month, 1)
                val from = first.plus(-7, DateTimeUnit.DAY)
                val to = first.plus(1, DateTimeUnit.MONTH).plus(7, DateTimeUnit.DAY)
                val rows = fetchRangeWithRetry(from, to, yearMonth) ?: return@launch
                ensureActive()
                val fetched = rows.toCalendarEntries(
                    ownerColors = _uiState.value.ownerColors,
                    viewerId = _uiState.value.myProfile?.id,
                )
                _uiState.update { it.copy(entriesByMonth = it.entriesByMonth + (yearMonth to fetched)) }
            } finally {
                // force 로 교체된 뒤 끝난 stale job 이 새 job 의 기록을 지우지 않도록 자기 자신일 때만 제거.
                if (inFlightMonths[yearMonth] === job) inFlightMonths -= yearMonth
            }
        }
        inFlightMonths[yearMonth] = job
        job.start()
    }

    /**
     * 범위 조회를 [MonthFetchRetryDelays] backoff 로 재시도 (최대 5회). 모두 실패하면 null — 캐시 유지.
     *
     * 시도마다 쓸 수 있는 세션 (만료 전 토큰) 을 먼저 확보한다. 세션 없이 조회하면 `myCoupleId()` 가 null 이 돼
     * 빈 목록이 "성공" 으로 돌아오기 때문 (#422). 조회 후에도 세션이 없으면 빈 결과는 실패로 본다.
     * VM 이 clear 되면 viewModelScope cancel 로 즉시 중단.
     *
     * @param from 조회 시작 날짜 (포함).
     * @param to 조회 끝 날짜 (포함).
     * @param yearMonth 로그용 대상 달.
     */
    private suspend fun fetchRangeWithRetry(
        from: LocalDate,
        to: LocalDate,
        yearMonth: YearMonth,
    ): List<SchedulesRepository.Row>? {
        val maxAttempts = MonthFetchRetryDelays.size + 1
        for (attempt in 0 until maxAttempts) {
            when (awaitUsableSession(expiryMargin = SessionExpiryMargin, logTag = "Schedule")) {
                SessionReadiness.Ready -> {
                    val result = try {
                        Result.success(SchedulesRepository.listInRange(from, to))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                    result.getOrNull()?.let { rows ->
                        if (rows.isNotEmpty() || SupabaseProvider.client.auth.currentSessionOrNull() != null) return rows
                        println("[Schedule] listInRange($yearMonth) 세션 없이 빈 결과 — 실패 처리 (${attempt + 1}/$maxAttempts)")
                    } ?: println("[Schedule] listInRange($yearMonth) 실패 (${attempt + 1}/$maxAttempts): ${result.exceptionOrNull()}")
                }
                // 로그아웃 확정 — 재시도 의미 없음. 캐시는 로그아웃 라우팅이 정리.
                SessionReadiness.LoggedOut -> return null
                SessionReadiness.Unavailable ->
                    println("[Schedule] listInRange($yearMonth) 세션 미준비 (${attempt + 1}/$maxAttempts)")
            }
            MonthFetchRetryDelays.getOrNull(attempt)?.let { delay(it) }
        }
        return null
    }

    /**
     * 세션 복구 감시 (#422). 토큰 갱신이 네트워크 문제로 실패하면 상태가 `RefreshFailure` 로 바뀌고 그 사이
     * 조회는 모두 실패한다. 라이브러리 자동 재시도로 다시 `Authenticated` 가 되면 프로필과 캐시에 없는 달을
     * throttle 없이 다시 받는다. 복구가 아니어도 새 세션이 들어왔는데 비어 있는 달이 있으면 채운다.
     */
    private fun observeSessionRecovery() {
        viewModelScope.launch {
            var previous: SessionStatus? = null
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                val recovered = status is SessionStatus.Authenticated && previous is SessionStatus.RefreshFailure
                previous = status
                if (status !is SessionStatus.Authenticated) return@collect
                if (recovered) {
                    println("[Main] 세션 복구 — 프로필 · 비어 있는 달 재조회")
                    refreshProfile()
                }
                monthsToRefetch(requestedMonths, _uiState.value.entriesByMonth.keys, includeCached = false)
                    .forEach { fetchAndReplaceMonth(it, force = recovered) }
            }
        }
    }

    /**
     * 앱이 다시 앞으로 올 때 (#422). 백그라운드 동안 실시간 소켓이 끊겨 놓친 변경을 따라잡는다.
     *
     * - 캐시에 없는 달 (조회 실패로 비어 있는 달 포함) 은 **항상** 다시 받는다. 끊긴 연결에 매달린 이전 조회는 cancel.
     * - 이미 캐시된 달 재조회 · 프로필 갱신은 30초 throttle. 첫 resume 은 진입 초기 로드와 겹치므로 생략.
     * - 프로필이 비어 있으면 (초기 조회 실패) throttle 과 무관하게 다시 받는다.
     */
    fun onResume() {
        val last = lastResumeRefresh
        val isFirstResume = last == null
        val throttlePassed = last != null && last.elapsedNow() >= ResumeRefreshInterval
        if (isFirstResume || throttlePassed) lastResumeRefresh = TimeSource.Monotonic.markNow()

        if (throttlePassed || (!isFirstResume && _uiState.value.myProfile == null)) refreshProfile()
        // 첫 resume 은 초기 로드 조회가 진행 중일 수 있어 중복만 거른다 (force=false).
        monthsToRefetch(requestedMonths, _uiState.value.entriesByMonth.keys, includeCached = throttlePassed)
            .forEach { fetchAndReplaceMonth(it, force = !isFirstResume) }
        startRealtime()
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
        (_uiState.value.entriesByMonth.keys + requestedMonths).toSet().forEach { fetchAndReplaceMonth(it) }
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
    /** 연결된 파트너 프로필. 드로워 커플 헤더 (#335) 의 두 번째 아바타 · 이름. 미연결이면 null. */
    val partnerProfile: com.hyunjine.linker.data.remote.UsersRepository.Profile? = null,
    val ownerColors: OwnerColors = OwnerColors.Default,
    val entriesByMonth: Map<YearMonth, Map<LocalDate, CalendarDayEntry>> = emptyMap(),
    /** 파트너 조인 여부. 드로워의 "상대방 캘린더" 토글 노출 · 캘린더 필터에 사용. */
    val hasPartner: Boolean = false,
)
