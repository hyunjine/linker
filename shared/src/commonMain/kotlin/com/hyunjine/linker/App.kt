package com.hyunjine.linker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.hyunjine.linker.auth.rememberKakaoLoginClient
import com.hyunjine.linker.auth.sessionStatus
import com.hyunjine.linker.auth.signInWithKakao
import com.hyunjine.linker.auth.signOut
import com.hyunjine.linker.data.remote.AnniversariesRepository
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.feature.anniversary.AnniversariesScreen
import com.hyunjine.linker.feature.anniversary.AnniversaryUi
import com.hyunjine.linker.feature.couple.CoupleInviteCodeScreen
import com.hyunjine.linker.feature.couple.CoupleJoinScreen
import com.hyunjine.linker.feature.couple.CoupleLinkScreen
import com.hyunjine.linker.feature.main.AllDaySchedule
import com.hyunjine.linker.feature.main.CalendarDayEntry
import com.hyunjine.linker.feature.main.CalendarEvent
import com.hyunjine.linker.feature.main.CalendarEventType
import com.hyunjine.linker.feature.main.DayDetail
import com.hyunjine.linker.feature.main.DayOwner
import com.hyunjine.linker.feature.main.DayTask
import com.hyunjine.linker.feature.main.OwnerColors
import com.hyunjine.linker.feature.main.TimedSchedule
import com.hyunjine.linker.feature.profile.ProfileSetupScreen
import com.hyunjine.linker.feature.schedule.CreateScheduleScreen
import com.hyunjine.linker.feature.search.SearchAnniversaryItem
import com.hyunjine.linker.feature.search.SearchResults
import com.hyunjine.linker.feature.search.SearchScheduleItem
import com.hyunjine.linker.feature.search.SearchScreen
import com.hyunjine.linker.feature.auth.AuthGateMode
import com.hyunjine.linker.feature.auth.AuthGateScreen
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.graphics.Color
import com.hyunjine.linker.platform.rememberCopyToClipboard
import com.hyunjine.linker.platform.rememberShareText
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * 앱 최상위 네비게이션 그래프. Navigation3 [NavDisplay] 로 백스택을 직접 소유한다.
 * 각 목적지 [NavKey] 는 `@Serializable` 이고, [NavConfig] 의 polymorphic 서브클래스로 등록해야
 * saved state 복원이 가능하다 (KMP 는 리플렉션이 없어 명시 등록 필수).
 */
/**
 * 스플래시 · 로그인을 하나의 컴포저블 안에서 애니메이션으로 이어주기 위해 두 라우트를 통합.
 * `SessionStatus` 값에 따라 [AuthGateScreen] 내부에서 mode 를 전환:
 *  - Initializing → splash 시각 상태 유지
 *  - NotAuthenticated / RefreshFailure → login 시각으로 슬라이드 + 카카오 버튼 fade-in
 */
@Serializable
private data object AuthRoute : NavKey

@Serializable
private data object MainRoute : NavKey

@Serializable
private data object ProfileSetupRoute : NavKey

@Serializable
private data object ProfileEditRoute : NavKey

@Serializable
private data object CoupleLinkRoute : NavKey

@Serializable
private data object CoupleInviteCodeRoute : NavKey

@Serializable
private data object CoupleJoinRoute : NavKey

/**
 * 스케줄 등록/수정 화면. [scheduleId] 가 null 이면 신규, 값이 있으면 그 id 의 스케줄을 로드해 편집.
 * [initialDate] 는 신규 저장 시 seed 할 시작·종료일 (ISO). 캘린더 셀 롱프레스 · DayDetailSheet "+"
 * 진입 경로에서 탭한 날짜를 전달. null 이면 오늘 날짜로 기본.
 */
@Serializable
private data class CreateScheduleRoute(
    val scheduleId: String? = null,
    val initialDate: String? = null,
    /** 신규 진입 시 draft.type 을 seed 할 값 ("task"|"schedule"). null 이면 기본 (Schedule). */
    val initialType: String? = null,
) : NavKey

@Serializable
private data object AnniversariesRoute : NavKey

@Serializable
private data object SearchRoute : NavKey

private val NavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AuthRoute::class, AuthRoute.serializer())
            subclass(MainRoute::class, MainRoute.serializer())
            subclass(ProfileSetupRoute::class, ProfileSetupRoute.serializer())
            subclass(ProfileEditRoute::class, ProfileEditRoute.serializer())
            subclass(CoupleLinkRoute::class, CoupleLinkRoute.serializer())
            subclass(CoupleInviteCodeRoute::class, CoupleInviteCodeRoute.serializer())
            subclass(CoupleJoinRoute::class, CoupleJoinRoute.serializer())
            subclass(CreateScheduleRoute::class, CreateScheduleRoute.serializer())
            subclass(AnniversariesRoute::class, AnniversariesRoute.serializer())
            subclass(SearchRoute::class, SearchRoute.serializer())
        }
    }
}

private fun oneMonthAgo(): LocalDate = today().plus(-31, DateTimeUnit.DAY)
private fun oneMonthAhead(): LocalDate = today().plus(93, DateTimeUnit.DAY)

/**
 * 로그인 완료 시 자동으로 진입할 화면을 결정한다.
 * - 프로필 미완성 → ProfileSetup
 * - 프로필 완성 → Main (커플 가입 여부와 무관 — 커플 연결은 드로워 "상대방 연결" 에서 언제든지)
 *
 * 조회 실패는 안전하게 ProfileSetup 으로 폴백 (사용자가 다시 시도할 수 있게 하는 게 최선).
 */
private suspend fun decideBootstrapTarget(): NavKey {
    val profile = runCatching { UsersRepository.myProfile() }
        .onFailure { println("[Boot] myProfile 실패: $it") }
        .getOrNull()
    return if (profile?.isCompleted != true) ProfileSetupRoute else MainRoute
}

@kotlin.OptIn(kotlin.time.ExperimentalTime::class)
private fun today(): LocalDate =
    kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        .date

/**
 * 스케줄 rows → DayDetailSheet 가 소비하는 [DayDetail].
 * - type='task' → [DayTask]
 * - type='schedule' + all_day → [AllDaySchedule]
 * - type='schedule' + 시각 → [TimedSchedule]
 */
private fun List<SchedulesRepository.Row>.toDayDetail(date: LocalDate): DayDetail {
    val tasks = mutableListOf<DayTask>()
    val timed = mutableListOf<TimedSchedule>()
    val allDay = mutableListOf<AllDaySchedule>()
    for (row in this) {
        val owner = row.ownerKind.toDayOwner()
        when {
            row.type == "task" -> tasks += DayTask(
                id = row.id, title = row.title, isDone = row.isDone, owner = owner,
            )
            row.allDay -> allDay += AllDaySchedule(
                id = row.id, title = row.title, owner = owner, barColor = null,
            )
            else -> timed += TimedSchedule(
                id = row.id,
                startTime = row.startTime.toKoreanClock() ?: "",
                endTime = row.endTime.toKoreanClock(),
                title = row.title,
                owner = owner,
            )
        }
    }
    return DayDetail(
        date = date,
        lunarLabel = null,   // 음력 표시는 후속 이슈
        tasks = tasks,
        timedSchedules = timed,
        allDaySchedules = allDay,
    )
}

private fun String.toDayOwner(): DayOwner = when (this) {
    "me" -> DayOwner.Me
    "partner" -> DayOwner.Partner
    else -> DayOwner.Us
}

/** "HH:MM:SS" → "오전 10:00" / "오후 2:00" 형식. null 은 null 그대로. */
private fun String?.toKoreanClock(): String? {
    if (this.isNullOrBlank()) return null
    val h = substring(0, 2).toIntOrNull() ?: return null
    val m = substring(3, 5)
    val (period, hour12) = when {
        h == 0 -> "오전" to 12
        h < 12 -> "오전" to h
        h == 12 -> "오후" to 12
        else -> "오후" to (h - 12)
    }
    return "$period $hour12:$m"
}

/** 기념일 서버 row → UI 데이터. */
private fun AnniversariesRepository.Row.toUi(): AnniversaryUi = AnniversaryUi(
    id = id,
    title = title,
    date = LocalDate.parse(date),
    repeatYearly = repeatYearly,
)

/**
 * 카카오 프로필 URL 은 http 로 내려와 Android 9+ · iOS ATS 가 cleartext 로 차단.
 * 렌더 직전에 https 로 강제. Kakao CDN 은 https 도 동일 경로로 서빙.
 */
private fun String?.toSecureImageUrl(): String? =
    this?.replace(Regex("^http://"), "https://")

/** ISO date (yyyy-MM-dd) → ProfileSetupScreen 이 파싱하는 "yyyy. MM. dd." 포맷. */
private fun isoToDisplayBirthDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return "2000. 01. 01."
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    return "${date.year}. $m. $d."
}

/** ISO date (yyyy-MM-dd) → 드로워 핸들 자리에 표시할 "yyyy.MM.dd" (점 사이 공백 없음). */
private fun isoToHandleBirthDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    return "${date.year}.$m.$d"
}

/** Kakao provider 가 채워준 user_metadata 에서 프로필 셋업 초기값 뽑는다. 값이 없으면 화면 기본값 그대로. */
private data class ProfileDefaults(val nickname: String, val avatarUrl: String?)

private fun profileDefaults(user: UserInfo?): ProfileDefaults {
    val meta: JsonObject? = user?.userMetadata
    val nickname = meta?.get("full_name")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
        ?: "현진"
    val avatar = meta?.get("avatar_url")?.jsonPrimitive?.contentOrNull
    return ProfileDefaults(nickname = nickname, avatarUrl = avatar)
}

@Composable
fun App() {
    LinkerTheme {
        val backStack = rememberNavBackStack(NavConfig, AuthRoute)
            val scope = rememberCoroutineScope()
            // 온보딩 완료(커플 연결) 시점에 로그인·프로필·연결 스택을 전부 비우고 Main 만 남긴다.
            // 홈에서 뒤로가기로 로그인 화면이 다시 뜨면 안 되므로 clear + push 조합.
            val goHome: () -> Unit = {
                backStack.clear()
                backStack.add(MainRoute)
            }
            // 프로필 편집 후 MainViewModel 이 프로필 · owner 색 재로드하도록 트리거.
            // ProfileEditRoute 저장 성공 시 bump → MainRoute 의 LaunchedEffect 가 VM.refreshProfile 호출.
            var profileRefreshTick by remember { mutableStateOf(0) }

            // 스케줄 저장/수정/삭제 후 캘린더 chip 을 재fetch 하도록 트리거.
            // CreateScheduleRoute onDone 시 bump → MainRoute 의 LaunchedEffect 가 VM.refreshSchedules 호출.
            var scheduleRefreshTick by remember { mutableStateOf(0) }

            // 커플 연결 상태가 바뀔 때마다 (join 성공 등) MainViewModel 이 파트너 프로필 · 색 · chip 재조회.
            // 드로워 "상대방 연결" 진입은 이 상태를 hide/show 로 반영.
            var coupleRefreshTick by remember { mutableStateOf(0) }

            // 세션 상태 기반 부트스트랩 라우팅.
            //  - Initializing / NotAuthenticated / RefreshFailure: AuthRoute 유지
            //    (내부 AuthGateScreen 이 mode 로 splash ↔ login 시각 전환)
            //  - Authenticated: 프로필/커플 상태 조회 → 미완성 단계로 자동 진입 (재로그인 시 온보딩 스킵)
            val status by sessionStatus.collectAsState()
            LaunchedEffect(status) {
                println("[Auth] sessionStatus = ${status::class.simpleName}")
                when (val s = status) {
                    is SessionStatus.Authenticated -> {
                        val target = decideBootstrapTarget()
                        println("[Auth] Authenticated → $target")
                        if (backStack.lastOrNull() != target) {
                            backStack.clear()
                            backStack.add(target)
                        }
                    }
                    is SessionStatus.NotAuthenticated,
                    is SessionStatus.RefreshFailure -> {
                        // AuthRoute 이 이미 최상단이면 유지 (내부에서 login mode 로 자동 전환).
                        // 다른 화면에서 로그아웃 상황 (RefreshFailure 등) 이면 AuthRoute 로 리셋.
                        if (backStack.lastOrNull() != AuthRoute) {
                            println("[Auth] 세션 없음 → AuthRoute 로 리셋")
                            backStack.clear()
                            backStack.add(AuthRoute)
                        }
                    }
                    is SessionStatus.Initializing -> Unit  // AuthRoute 그대로 유지 (splash mode)
                }
            }

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<AuthRoute> {
                        // 스플래시 최소 노출 시간. 세션이 이보다 빠르게 로드돼도 로고 감상 +
                        // login 슬라이드 애니메이션이 자연스레 재생되도록 유지.
                        var minSplashElapsed by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(1500L)
                            minSplashElapsed = true
                        }
                        // 세션 로딩 중이거나 최소 노출시간 미충족이면 splash, 아니면 login.
                        // 로고 · 타이틀은 유지된 채 위치만 슬라이드.
                        val mode = if (status is SessionStatus.Initializing || !minSplashElapsed) {
                            AuthGateMode.Splash
                        } else {
                            AuthGateMode.Login
                        }
                        com.hyunjine.linker.feature.login.LoginRoute(mode = mode)
                    }
                    entry<ProfileSetupRoute> {
                        val currentUser = (status as? SessionStatus.Authenticated)?.session?.user
                        com.hyunjine.linker.feature.profile.ProfileSetupRoute(
                            currentUser = currentUser,
                            onBack = { backStack.removeLastOrNull() },
                            // 프로필 세팅 완료 → 커플 연결은 선택이라 바로 홈. 원할 때 드로워로 연결.
                            onSaved = goHome,
                        )
                    }
                    entry<ProfileEditRoute> {
                        com.hyunjine.linker.feature.profile.ProfileEditRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onSaved = {
                                profileRefreshTick++
                                backStack.removeLastOrNull()
                            },
                        )
                    }
                    entry<CoupleLinkRoute> {
                        com.hyunjine.linker.feature.couple.CoupleLinkRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onCreateInvite = { backStack.add(CoupleInviteCodeRoute) },
                            onEnterPartnerCode = { backStack.add(CoupleJoinRoute) },
                            onUnlinked = {
                                // Unlink 성공 → 새 solo couple 로 옮겨감. Main 이 프로필 · 색 · chip
                                // · realtime 채널을 새 couple 기준으로 다시 잡도록 tick 올림.
                                coupleRefreshTick++
                            },
                        )
                    }
                    entry<CoupleInviteCodeRoute> {
                        com.hyunjine.linker.feature.couple.CoupleInviteCodeRoute(
                            onBack = {
                                // 이 화면 진입만으로 createOrGetMyCouple 이 호출돼 유저가 "커플 있음" 상태로
                                // 바뀔 수 있음 → 돌아갈 때 Main 이 상태 재조회하도록 tick bump.
                                coupleRefreshTick++
                                backStack.removeLastOrNull()
                            },
                        )
                    }
                    entry<CoupleJoinRoute> {
                        com.hyunjine.linker.feature.couple.CoupleJoinRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onJoined = {
                                // Main 에서 진입한 케이스: CoupleJoin · CoupleLink 만 걷어내고 Main 유지.
                                // 온보딩 직후 진입은 없어졌지만 안전하게 Main 이 스택에 없으면 goHome 폴백.
                                while (backStack.isNotEmpty() && backStack.last() != MainRoute) {
                                    backStack.removeLastOrNull()
                                }
                                if (backStack.isEmpty()) goHome()
                                coupleRefreshTick++
                            },
                        )
                    }
                    entry<MainRoute> {
                        val kakao = rememberKakaoLoginClient()
                        com.hyunjine.linker.feature.main.MainRoute(
                            onAddSchedule = { tappedDate, type ->
                                backStack.add(
                                    CreateScheduleRoute(
                                        initialDate = tappedDate.toString(),
                                        initialType = when (type) {
                                            com.hyunjine.linker.feature.schedule.ScheduleType.Task -> "task"
                                            com.hyunjine.linker.feature.schedule.ScheduleType.Schedule -> "schedule"
                                        },
                                    ),
                                )
                            },
                            onEditSchedule = { id -> backStack.add(CreateScheduleRoute(id)) },
                            onAnniversaryClick = { backStack.add(AnniversariesRoute) },
                            onSearchClick = { backStack.add(SearchRoute) },
                            onProfileEditClick = { backStack.add(ProfileEditRoute) },
                            onCoupleLinkClick = { backStack.add(CoupleLinkRoute) },
                            onLogout = {
                                scope.launch {
                                    runCatching { signOut(kakao) }
                                        .onFailure { println("[Auth] signOut 실패: $it") }
                                }
                            },
                            profileRefreshTick = profileRefreshTick,
                            scheduleRefreshTick = scheduleRefreshTick,
                            coupleRefreshTick = coupleRefreshTick,
                        )
                    }
                    entry<SearchRoute> {
                        com.hyunjine.linker.feature.search.SearchRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onScheduleClick = { id -> backStack.add(CreateScheduleRoute(id)) },
                            onAnniversaryClick = { _ -> backStack.add(AnniversariesRoute) },
                        )
                    }
                    entry<AnniversariesRoute> {
                        com.hyunjine.linker.feature.anniversary.AnniversariesRoute(
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CreateScheduleRoute> { route ->
                        com.hyunjine.linker.feature.schedule.CreateScheduleRoute(
                            scheduleId = route.scheduleId,
                            initialDate = route.initialDate,
                            initialType = route.initialType,
                            onBack = { backStack.removeLastOrNull() },
                            onDone = {
                                scheduleRefreshTick++
                                backStack.removeLastOrNull()
                            },
                        )
                    }
                },
            )
    }
}
