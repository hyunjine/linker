package com.hyunjine.linker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.hyunjine.linker.auth.OutlookAuthResult
import com.hyunjine.linker.auth.rememberOutlookAuthClient
import com.hyunjine.linker.auth.sessionStatus
import com.hyunjine.linker.auth.signOut
import com.hyunjine.linker.data.local.OutlookLocal
import com.hyunjine.linker.data.remote.AnniversariesRepository
import com.hyunjine.linker.data.remote.OutlookSyncService
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.feature.anniversary.AnniversaryUi
import com.hyunjine.linker.feature.auth.AuthGateMode
import com.hyunjine.linker.feature.auth.AuthGateScreen
import com.hyunjine.linker.platform.ensureCurrentDeviceRegistered
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** 스플래시가 이 시간 이상 유지되면 하단에 네트워크 확인 안내를 노출 (#424). */
private const val SPLASH_NETWORK_HINT_DELAY_MS = 5_000L

/**
 * 앱 최상위 네비게이션 그래프. Navigation3 [NavDisplay] 로 백스택을 직접 소유한다.
 * 각 목적지 [NavKey] 는 `@Serializable` 이고, [NavConfig] 의 polymorphic 서브클래스로 등록해야
 * saved state 복원이 가능하다 (KMP 는 리플렉션이 없어 명시 등록 필수).
 */
/**
 * 스플래시 · 로그인을 하나의 컴포저블 안에서 애니메이션으로 이어주기 위해 두 라우트를 통합.
 * `SessionStatus` 값에 따라 [AuthGateScreen] 내부에서 mode 를 전환:
 *  - Initializing · Authenticated (부트스트랩 프로필 확인 중) · RefreshFailure (일시 오류, 재시도 중)
 *    → splash 시각 상태 유지
 *  - NotAuthenticated → login 시각으로 슬라이드 + 카카오 버튼 fade-in
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

/** 디데이 라우트 (#329). 드로워 하단 "기념일" 탭 진입점. */
@Serializable
private data object DdayRoute : NavKey

@Serializable
private data object SearchRoute : NavKey

@Serializable
private data object ReleaseNotesRoute : NavKey

/** 더보기 라우트 (#385). 드로워 하단 "더보기" 탭 — 릴리즈 노트 · 버전 · 로그아웃. */
@Serializable
private data object MoreRoute : NavKey

/** 알림 내역 라우트 (#303). 메인 상단바 종 아이콘 진입점. */
@Serializable
private data object NotificationsRoute : NavKey

/** 할 일 내역 라우트 (#304). 드로워 "할 일" 진입점. */
@Serializable
private data object TasksRoute : NavKey

@Serializable
private data object EverytimeTimetableRoute : NavKey

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
            subclass(DdayRoute::class, DdayRoute.serializer())
            subclass(SearchRoute::class, SearchRoute.serializer())
            subclass(ReleaseNotesRoute::class, ReleaseNotesRoute.serializer())
            subclass(MoreRoute::class, MoreRoute.serializer())
            subclass(TasksRoute::class, TasksRoute.serializer())
            subclass(NotificationsRoute::class, NotificationsRoute.serializer())
            subclass(EverytimeTimetableRoute::class, EverytimeTimetableRoute.serializer())
        }
    }
}

/**
 * 로그인 완료 시 자동으로 진입할 화면을 결정한다.
 * - 프로필 미완성 → ProfileSetup
 * - 프로필 완성 → Main (커플 가입 여부와 무관 — 커플 연결은 드로워 "상대방 연결" 에서 언제든지)
 *
 * 조회는 재시도하며, 실패를 미완성으로 취급하지 않는다 (#424). 끝내 확인 못하면 Main
 * — 판단 근거는 [bootstrapDestinationOf] 참고.
 */
private suspend fun decideBootstrapTarget(): NavKey {
    val state = fetchBootstrapProfileState()
    return when (bootstrapDestinationOf(state)) {
        BootstrapDestination.ProfileSetup -> ProfileSetupRoute
        BootstrapDestination.Main -> MainRoute
    }
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
    val m = (date.month.ordinal + 1).toString().padStart(2, '0')
    val d = date.day.toString().padStart(2, '0')
    return "${date.year}. $m. $d."
}

/** ISO date (yyyy-MM-dd) → 드로워 핸들 자리에 표시할 "yyyy.MM.dd" (점 사이 공백 없음). */
private fun isoToHandleBirthDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    val m = (date.month.ordinal + 1).toString().padStart(2, '0')
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

            // Outlook 재택 일정 연동 (#242). 더보기 "캘린더 연동" 에서 연결 · 해제하고, 연결돼 있으면
            // 세션 복원 · 커플 변경 시 자동 동기화. 캐시된 MSAL 계정이 없으면 sync 는 no-op.
            val outlookAuth = rememberOutlookAuthClient()
            val outlookSync = remember(outlookAuth) { OutlookSyncService(outlookAuth) }
            var outlook by remember {
                mutableStateOf(com.hyunjine.linker.feature.more.OutlookLinkState(teamName = OutlookLocal.teamName))
            }
            // 서버에 "Outlook 연동됨" 을 이번 실행에서 이미 표시했는지 (#420). sync 마다 쓰지 않도록.
            var outlookConnectedMarked by remember { mutableStateOf(false) }
            // 재택 미러 동기화 (약 2달 전 ~ 3달 뒤, OutlookSyncService.defaultRange). 성공하면 캘린더 chip 재조회.
            // 오류는 삼키고 로그만. 성공하면 서버에 연동 표시 → 30분 주기 silent push 대상이 된다 (#420).
            val syncOutlook: suspend () -> Boolean = {
                val (from, to) = OutlookSyncService.defaultRange()
                val synced = runCatching { outlookSync.syncRange(from, to) }
                    .onFailure { println("[Outlook] sync 실패: $it") }
                    .getOrNull()
                    ?.also { scheduleRefreshTick++ } != null
                if (synced && !outlookConnectedMarked) {
                    outlookConnectedMarked = true
                    // 표시 실패는 연동 흐름과 무관 — 다음 실행에서 다시 시도.
                    scope.launch { if (!UsersRepository.setOutlookConnected(true)) outlookConnectedMarked = false }
                }
                synced
            }
            // 앱이 다시 앞으로 올 때도 동기화 (#242). 콜드 스타트엔 세션 복원 쪽 sync 와 겹치므로
            // 마지막 sync 후 1분 안이면 건너뛴다.
            var lastOutlookResumeSync by remember { mutableStateOf<TimeMark?>(null) }
            LifecycleResumeEffect(Unit) {
                val recent = lastOutlookResumeSync?.let { it.elapsedNow() < 1.minutes } ?: false
                if (!recent && sessionStatus.value is SessionStatus.Authenticated && outlook.email != null) {
                    lastOutlookResumeSync = TimeSource.Monotonic.markNow()
                    scope.launch { syncOutlook() }
                }
                onPauseOrDispose { }
            }

            // 세션 상태 기반 부트스트랩 라우팅.
            //  - Initializing / NotAuthenticated: AuthRoute 유지
            //    (내부 AuthGateScreen 이 mode 로 splash ↔ login 시각 전환)
            //  - Authenticated: 프로필/커플 상태 조회 → 미완성 단계로 자동 진입 (재로그인 시 온보딩 스킵)
            //  - RefreshFailure: 현재 백스택 유지 (일시적 네트워크 오류, supabase 가 재시도)
            val status by sessionStatus.collectAsState()
            // 세션 · 커플 상태 변할 때 저장된 MSAL 계정 있으면 상단 상태에 반영 + 초기 sync 시도.
            // MSAL/Graph/네트워크 오류는 여기서 무조건 catch — 앱 부팅 flow 를 절대 막지 않도록.
            LaunchedEffect(status, coupleRefreshTick) {
                if (status is SessionStatus.Authenticated) {
                    runCatching { outlookAuth.currentAccount()?.email }
                        .onSuccess { outlook = outlook.copy(email = it) }
                        .onFailure { println("[Outlook] currentAccount 실패: $it") }
                    if (outlook.email != null) {
                        lastOutlookResumeSync = TimeSource.Monotonic.markNow()
                        syncOutlook()
                    }
                }
            }
            // 이번 로그인 세션에서 부트스트랩 라우팅을 이미 마쳤는지 (#424). 토큰 갱신 등으로
            // Authenticated 가 다시 흘러와도 사용자가 보고 있는 화면을 갈아엎지 않도록 1회만 라우팅.
            // 로그아웃 (NotAuthenticated) 시에만 false 로 되돌린다. rememberSaveable 이라 Android 구성 변경 ·
            // 프로세스 복원 후 백스택이 복원돼도 다시 라우팅하지 않는다.
            var bootstrapped by rememberSaveable { mutableStateOf(false) }
            // key 를 status 인스턴스가 아닌 타입으로 둬서, 부트스트랩 (프로필 재시도) 진행 중
            // Authenticated(Refresh) 재방출이 와도 코루틴이 취소 · 재시작되지 않게 한다.
            LaunchedEffect(status::class) {
                println("[Auth] sessionStatus = ${status::class.simpleName}")
                when (val s = status) {
                    is SessionStatus.Authenticated -> {
                        if (bootstrapped) {
                            println("[Auth] Authenticated(${s.source::class.simpleName}) — 부트스트랩 완료 상태라 라우팅 생략")
                            return@LaunchedEffect
                        }
                        // #317 로그인 성공 시 FCM device 등록 강제 트리거. 세션 복원 · 신규 로그인
                        // 모두 이 분기로 흘러오고, Repository 가 (user_id, fcm_token) unique 로
                        // dedupe 하므로 idempotent 안전. 계정 스왑 (test1 → test2) 케이스에서
                        // onNewToken 이 fire 되지 않아 test2 device row 가 없던 문제를 커버.
                        // 화면 이동과 무관하므로 기다리지 않는다 (#425). 라우팅 (backStack 교체) 이나
                        // status 변화로 이 effect 가 취소돼도 등록은 끝까지 가도록 App 레벨 scope 에서 실행.
                        scope.launch {
                            runCatching { ensureCurrentDeviceRegistered() }
                                .onFailure { println("[FCM] ensureCurrentDeviceRegistered 실패: $it") }
                        }
                        val target = decideBootstrapTarget()
                        println("[Auth] Authenticated → $target")
                        if (backStack.lastOrNull() != target) {
                            backStack.clear()
                            backStack.add(target)
                        }
                        bootstrapped = true
                    }
                    is SessionStatus.RefreshFailure -> {
                        // 자동 토큰 갱신 중 네트워크 · 서버 오류 (예: 백그라운드 복귀 직후 만료 토큰).
                        // 세션은 남아 있고 supabase-kt 가 잠시 뒤 재시도해 Authenticated 로 돌아오므로
                        // 로그아웃으로 취급하지 않는다 (#424). 보던 화면 · bootstrapped 플래그 유지 →
                        // 재-Authenticated 때 재라우팅 없음. 스플래시 중이면 splash 그대로 (아래 AuthRoute mode).
                        println("[Auth] RefreshFailure — 백스택 유지, 재시도 대기")
                    }
                    is SessionStatus.NotAuthenticated -> {
                        // AuthRoute 이 이미 최상단이면 유지 (내부에서 login mode 로 자동 전환).
                        // 다른 화면에서 로그아웃 (직접 로그아웃 · refresh token 거부 등) 이면 AuthRoute 로 리셋.
                        bootstrapped = false
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
                        // 로그인 필요가 확정 (NotAuthenticated) 되고 최소 노출시간이 지나야 login.
                        // 세션 로딩 중 · Authenticated 후 프로필 확인 (부트스트랩) 중 · RefreshFailure (재시도 대기)
                        // 에는 splash 유지 (#424) —
                        // 예전엔 Initializing 만 봐서, 프로필 조회가 1.5s 를 넘기면 login 레이아웃이 잠깐 비쳤다.
                        // 한 번 login 을 보여준 뒤 (소셜 로그인 성공 → 부트스트랩) 에는 splash 로 되돌리지 않는다.
                        // 로고 · 타이틀은 유지된 채 위치만 슬라이드.
                        var loginShown by remember { mutableStateOf(false) }
                        val signedOut = status is SessionStatus.NotAuthenticated
                        val mode = if (minSplashElapsed && (signedOut || loginShown)) {
                            AuthGateMode.Login
                        } else {
                            AuthGateMode.Splash
                        }
                        LaunchedEffect(mode) { if (mode == AuthGateMode.Login) loginShown = true }
                        // 스플래시가 5초 넘게 유지되면 (오프라인 + 만료 토큰 → RefreshFailure 재시도,
                        // 세션 로딩 · 부트스트랩 프로필 조회가 네트워크 대기) 하단에 네트워크 확인 안내 (#424).
                        // Splash 로 들어올 때마다 타이머를 새로 시작하고, Login 전환 · 라우팅 (AuthRoute 이탈)
                        // 시 자연히 사라진다. NotAuthenticated 확정이면 곧 login 으로 바뀌므로 노출 안 함.
                        var splashStalled by remember { mutableStateOf(false) }
                        LaunchedEffect(mode) {
                            splashStalled = false
                            if (mode == AuthGateMode.Splash) {
                                delay(SPLASH_NETWORK_HINT_DELAY_MS)
                                splashStalled = true
                            }
                        }
                        com.hyunjine.linker.feature.login.LoginRoute(
                            mode = mode,
                            networkHint = splashStalled && !signedOut,
                        )
                    }
                    entry<ProfileSetupRoute> {
                        val currentUser = (status as? SessionStatus.Authenticated)?.session?.user
                        // 온보딩 중 뒤로가기 = 로그아웃. 프로필 미완성 상태로 아무데도 갈 곳이 없어
                        // 스택 pop 은 no-op 이 되어버림. Supabase signOut → sessionStatus NotAuthenticated
                        // → LaunchedEffect 가 AuthRoute 로 리셋해 로그인 화면 복귀.
                        com.hyunjine.linker.feature.profile.ProfileSetupRoute(
                            currentUser = currentUser,
                            onBack = {
                                scope.launch {
                                    runCatching { signOut() }
                                        .onFailure { println("[Auth] ProfileSetup 취소 signOut 실패: $it") }
                                }
                            },
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
                            // 드로워 하단 "기념일" 탭 → 디데이 화면 (#329). 기존
                            // AnniversariesRoute (다건 기념일 리스트) 는 검색 결과 진입용으로만 유지.
                            onAnniversaryClick = { backStack.add(DdayRoute) },
                            onSearchClick = { backStack.add(SearchRoute) },
                            onNotificationsClick = { backStack.add(NotificationsRoute) },
                            onProfileEditClick = { backStack.add(ProfileEditRoute) },
                            onCoupleLinkClick = { backStack.add(CoupleLinkRoute) },
                            onTasksClick = { backStack.add(TasksRoute) },
                            onMoreClick = { backStack.add(MoreRoute) },
                            onEverytimeTimetableClick = { backStack.add(EverytimeTimetableRoute) },
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
                    entry<DdayRoute> {
                        com.hyunjine.linker.feature.dday.DdayRoute(
                            onBack = { backStack.removeLastOrNull() },
                            // 저장 · "설정된 상태" 전환은 후속 커밋 (#329). 우선은 flow 확인용.
                            onConfirmDate = { /* TODO: persist + switch to filled state */ },
                        )
                    }
                    entry<MoreRoute> {
                        com.hyunjine.linker.feature.more.MoreRoute(
                            outlook = outlook,
                            onBack = { backStack.removeLastOrNull() },
                            onReleaseNotesClick = { backStack.add(ReleaseNotesRoute) },
                            // 로그인 → 바로 첫 동기화. 실패 · 취소는 더보기 안내 문구로.
                            onOutlookConnect = {
                                scope.launch {
                                    outlook = outlook.copy(busy = true, error = null)
                                    when (val r = outlookAuth.login()) {
                                        is OutlookAuthResult.Success -> {
                                            outlook = outlook.copy(email = r.email)
                                            // 이름이 없으면 더보기가 입력 시트를 띄우고, 저장 시 동기화.
                                            if (outlook.teamName != null && !syncOutlook()) {
                                                outlook = outlook.copy(error = "재택 일정을 가져오지 못했어요. 앱을 다시 열면 재시도해요.")
                                            }
                                        }
                                        OutlookAuthResult.Cancelled -> Unit
                                        is OutlookAuthResult.Failure -> {
                                            println("[Outlook] login 실패: ${r.reason}")
                                            outlook = outlook.copy(error = "Outlook 에 연결하지 못했어요. 회사 계정으로 다시 시도해 주세요.")
                                        }
                                    }
                                    outlook = outlook.copy(busy = false)
                                }
                            },
                            // 팀 캘린더 이름 저장 → 그 이름으로 다시 동기화.
                            onOutlookNameChange = { name ->
                                OutlookLocal.teamName = name
                                scope.launch {
                                    outlook = outlook.copy(teamName = name, busy = true, error = null)
                                    if (!syncOutlook()) {
                                        outlook = outlook.copy(error = "재택 일정을 가져오지 못했어요. TeamAIVN 캘린더가 공유돼 있는지 확인해 주세요.")
                                    }
                                    outlook = outlook.copy(busy = false)
                                }
                            },
                            // MSAL 계정 삭제 + 재택 미러 삭제 (파트너 화면에서도 사라짐).
                            onOutlookDisconnect = {
                                scope.launch {
                                    outlook = outlook.copy(busy = true, error = null)
                                    runCatching { outlookSync.disconnect() }
                                        .onSuccess {
                                            OutlookLocal.teamName = null
                                            outlook = com.hyunjine.linker.feature.more.OutlookLinkState()
                                            scheduleRefreshTick++
                                            // 서버 연동 표시 해제 → 주기 push 중단 (#420). 실패해도 해제 흐름은 그대로 (로그만).
                                            outlookConnectedMarked = false
                                            UsersRepository.setOutlookConnected(false)
                                        }
                                        .onFailure {
                                            println("[Outlook] disconnect 실패: $it")
                                            outlook = outlook.copy(busy = false, error = "연결을 해제하지 못했어요. 잠시 후 다시 시도해 주세요.")
                                        }
                                }
                            },
                            // 세션 종료 → sessionStatus NotAuthenticated → 위 LaunchedEffect 가 AuthRoute 로 리셋.
                            onLogout = {
                                scope.launch {
                                    runCatching { signOut() }
                                        .onFailure { println("[Auth] signOut 실패: $it") }
                                }
                            },
                        )
                    }
                    entry<ReleaseNotesRoute> {
                        com.hyunjine.linker.feature.release.ReleaseNotesRoute(
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<NotificationsRoute> {
                        com.hyunjine.linker.feature.notification.NotificationsRoute(
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<TasksRoute> {
                        com.hyunjine.linker.feature.task.TasksRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onEditTask = { id -> backStack.add(CreateScheduleRoute(id)) },
                            // 체크 토글 후 캘린더 chip 도 맞춰지도록 Main 재fetch 트리거.
                            onTasksChanged = { scheduleRefreshTick++ },
                            scheduleRefreshTick = scheduleRefreshTick,
                        )
                    }
                    entry<EverytimeTimetableRoute> {
                        com.hyunjine.linker.feature.everytime.EverytimeTimetableRoute(
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
