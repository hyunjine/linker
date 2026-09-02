package com.hyunjine.linker.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.feature.schedule.ScheduleType
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Nav 진입/이탈에도 살아있는 [MainViewModel] 을 붙여 `MainScreen` 을 렌더한다.
 * App.kt 의 nav entry 는 route 콜백만 넘기고, 내부 state · 로드 로직은 여기서 완결.
 *
 * @param onAddSchedule 롱프레스 · "+" 시트에서 일정 생성 진입.
 * @param onEditSchedule chip · 상세 시트에서 편집 진입.
 * @param onAnniversaryClick 드로워 "기념일 설정" 진입.
 * @param onSearchClick 상단바 검색 아이콘 → 검색 화면.
 * @param onProfileEditClick 드로워 프로필 헤더 탭 → 프로필 수정.
 * @param onLogout 드로워 로그아웃.
 */
@Composable
fun MainRoute(
    onAddSchedule: (LocalDate, ScheduleType) -> Unit,
    onEditSchedule: (String) -> Unit,
    onAnniversaryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileEditClick: () -> Unit,
    onCoupleLinkClick: () -> Unit,
    onLogout: () -> Unit,
    profileRefreshTick: Int,
    scheduleRefreshTick: Int,
    coupleRefreshTick: Int,
    onDrawerProfileHandle: (nickname: String, birthDate: String?, imageUrl: String?) -> Unit = { _, _, _ -> },
) {
    val viewModel: MainViewModel = viewModel { MainViewModel() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayState by viewModel.drawerDisplay.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // profileRefreshTick 이 바뀔 때마다 (프로필 편집 후) 다시 로드.
    LaunchedEffect(profileRefreshTick) { viewModel.refreshProfile() }

    // scheduleRefreshTick 이 바뀔 때마다 (스케줄 저장/수정/삭제 후) 이미 뜬 달들만 즉시 재fetch.
    // 초기값 0 무한 재fetch 방지 위해 tick > 0 일 때만.
    LaunchedEffect(scheduleRefreshTick) {
        if (scheduleRefreshTick > 0) viewModel.refreshSchedules()
    }

    // 커플 소속 변경 시 (join · invite 생성 등) 프로필 · 색 · chip 재로드 + realtime 채널 재구성.
    LaunchedEffect(coupleRefreshTick) {
        if (coupleRefreshTick > 0) {
            viewModel.refreshProfile()
            viewModel.restartRealtime()
        }
    }

    // 진입 시 postgres_changes 구독 시작 (viewModelScope 로 라이프사이클 관리).
    LaunchedEffect(Unit) { viewModel.startRealtime() }

    // 드로워 헤더용 파생값을 상위에 노출 (아직 App.kt 가 드로워 밖 다른 곳에 쓸 여지 대비).
    LaunchedEffect(uiState.myProfile) {
        val p = uiState.myProfile
        onDrawerProfileHandle(p?.nickname.orEmpty(), p?.birthDate, p?.profileImageUrl)
    }

    MainScreen(
        entriesByMonth = uiState.entriesByMonth,
        onMonthVisible = viewModel::loadMonthIfNeeded,
        onLoadDayDetail = { viewModel.loadDayDetail(it) },
        onToggleTaskDone = { id, done ->
            scope.launch { viewModel.toggleTaskDone(id, done) }
        },
        onAddSchedule = onAddSchedule,
        onEditSchedule = onEditSchedule,
        onAnniversaryClick = onAnniversaryClick,
        onSearchClick = onSearchClick,
        onProfileEditClick = onProfileEditClick,
        onCoupleLinkClick = onCoupleLinkClick,
        onLogout = onLogout,
        profileName = uiState.myProfile?.nickname.orEmpty(),
        profileHandle = uiState.myProfile?.birthDate?.let(::isoToHandleBirthDate).orEmpty(),
        profileImageUrl = uiState.myProfile?.profileImageUrl?.toSecureImageUrl(),
        displayState = displayState,
        onDisplayStateChange = viewModel::updateDrawerDisplay,
        hasPartner = uiState.hasPartner,
    )
}

/** ISO date → 드로워 핸들 자리에 표시할 "yyyy.MM.dd". */
private fun isoToHandleBirthDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    return "${date.year}.$m.$d"
}

/** Kakao CDN 이 http 로 URL 을 내려주는데 Android 9+ · iOS ATS 가 차단 → https 로 강제. */
private fun String?.toSecureImageUrl(): String? =
    this?.replace(Regex("^http://"), "https://")
