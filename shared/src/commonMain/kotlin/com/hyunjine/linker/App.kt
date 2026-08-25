package com.hyunjine.linker

import androidx.compose.material3.MaterialTheme
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
import com.hyunjine.linker.auth.sessionStatus
import com.hyunjine.linker.auth.signInWithKakao
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.ui.couple.CoupleLinkScreen
import com.hyunjine.linker.ui.login.LoginScreen
import com.hyunjine.linker.ui.main.CalendarDayEntry
import com.hyunjine.linker.ui.main.CalendarEvent
import com.hyunjine.linker.ui.main.CalendarEventType
import com.hyunjine.linker.ui.main.MainScreen
import com.hyunjine.linker.ui.profile.ProfileSetupScreen
import com.hyunjine.linker.ui.schedule.CreateScheduleScreen
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.hyunjine.linker.ui.theme.ProvidePretendard
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
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
@Serializable
private data object LoginRoute : NavKey

@Serializable
private data object MainRoute : NavKey

@Serializable
private data object ProfileSetupRoute : NavKey

@Serializable
private data object CoupleLinkRoute : NavKey

@Serializable
private data object CreateScheduleRoute : NavKey

private val NavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LoginRoute::class, LoginRoute.serializer())
            subclass(MainRoute::class, MainRoute.serializer())
            subclass(ProfileSetupRoute::class, ProfileSetupRoute.serializer())
            subclass(CoupleLinkRoute::class, CoupleLinkRoute.serializer())
            subclass(CreateScheduleRoute::class, CreateScheduleRoute.serializer())
        }
    }
}

private fun oneMonthAgo(): LocalDate = today().plus(-31, DateTimeUnit.DAY)
private fun oneMonthAhead(): LocalDate = today().plus(93, DateTimeUnit.DAY)

/**
 * 로그인 완료 시 자동으로 진입할 화면을 결정한다.
 * - 프로필 미완성 → ProfileSetup
 * - 프로필 완성 · 커플 미가입 → CoupleLink
 * - 둘 다 완료 → Main (홈)
 *
 * 조회 실패는 안전하게 ProfileSetup 으로 폴백 (사용자가 다시 시도할 수 있게 하는 게 최선).
 */
private suspend fun decideBootstrapTarget(): NavKey {
    val profile = runCatching { UsersRepository.myProfile() }
        .onFailure { println("[Boot] myProfile 실패: $it") }
        .getOrNull()
    if (profile?.isCompleted != true) return ProfileSetupRoute
    val coupleId = runCatching { CouplesRepository.myCoupleIdOrNull() }
        .onFailure { println("[Boot] myCoupleIdOrNull 실패: $it") }
        .getOrNull()
    return if (coupleId == null) CoupleLinkRoute else MainRoute
}

@kotlin.OptIn(kotlin.time.ExperimentalTime::class)
private fun today(): LocalDate =
    kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        .date

/**
 * 서버에서 받은 스케줄 rows 를 MainScreen 이 소비하는 `Map<LocalDate, CalendarDayEntry>` 로 변환.
 * 스케줄이 [start_date, end_date] 범위를 커버하면 각 날짜에 chip 을 추가한다.
 */
private fun List<SchedulesRepository.Row>.toCalendarEntries(): Map<LocalDate, CalendarDayEntry> {
    val out = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    for (row in this) {
        val start = LocalDate.parse(row.startDate)
        val end = LocalDate.parse(row.endDate)
        var d = start
        while (d <= end) {
            out.getOrPut(d) { mutableListOf() }
                .add(CalendarEvent(row.title, CalendarEventType.Personal))
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return out.mapValues { CalendarDayEntry(events = it.value.toList()) }
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
    MaterialTheme {
        ProvidePretendard {
            val backStack = rememberNavBackStack(NavConfig, LoginRoute)
            val scope = rememberCoroutineScope()
            // 온보딩 완료(커플 연결) 시점에 로그인·프로필·연결 스택을 전부 비우고 Main 만 남긴다.
            // 홈에서 뒤로가기로 로그인 화면이 다시 뜨면 안 되므로 clear + push 조합.
            val goHome: () -> Unit = {
                backStack.clear()
                backStack.add(MainRoute)
            }

            // 세션 상태 기반 부트스트랩 라우팅.
            //  - Authenticated: 프로필/커플 상태 조회 → 미완성 단계로 자동 진입 (재로그인 시 온보딩 스킵)
            //  - NotAuthenticated / RefreshFailure: 로그인 화면으로 스택 초기화
            //  - Initializing: 아무것도 안 함 (세션 storage 로드 중)
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
                        if (backStack.lastOrNull() != LoginRoute) {
                            println("[Auth] 세션 없음 → LoginRoute 로 리셋")
                            backStack.clear()
                            backStack.add(LoginRoute)
                        }
                    }
                    is SessionStatus.Initializing -> Unit
                }
            }

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<LoginRoute> {
                        LoginScreen(
                            onKakaoLoginClick = {
                                println("[Auth] 카카오 버튼 click")
                                scope.launch {
                                    runCatching { signInWithKakao() }
                                        .onFailure { println("[Auth] signInWithKakao 실패: $it") }
                                }
                            },
                        )
                    }
                    entry<ProfileSetupRoute> {
                        val currentUser = (status as? SessionStatus.Authenticated)?.session?.user
                        val defaults = remember(currentUser) { profileDefaults(currentUser) }
                        var saving by remember { mutableStateOf(false) }
                        ProfileSetupScreen(
                            nickname = defaults.nickname,
                            defaultAvatarUrl = defaults.avatarUrl,
                            saving = saving,
                            onBack = { backStack.removeLastOrNull() },
                            onNext = { nickname, birthDate, colorId ->
                                saving = true
                                scope.launch {
                                    runCatching {
                                        UsersRepository.completeProfile(
                                            nickname = nickname,
                                            birthDate = birthDate,
                                            profileImageUrl = defaults.avatarUrl,
                                            calendarColor = colorId,
                                        )
                                    }.onSuccess {
                                        println("[Profile] 저장 성공 → CoupleLink 로 이동")
                                        saving = false
                                        backStack.add(CoupleLinkRoute)
                                    }.onFailure {
                                        println("[Profile] 저장 실패: $it")
                                        saving = false
                                    }
                                }
                            },
                        )
                    }
                    entry<CoupleLinkRoute> {
                        var myCode by remember { mutableStateOf<String?>(null) }
                        var linking by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            runCatching { CouplesRepository.createOrGetMyCouple() }
                                .onSuccess {
                                    println("[Couple] my couple id=${it.id} code=${it.inviteCode}")
                                    myCode = it.inviteCode
                                }
                                .onFailure { println("[Couple] createOrGetMyCouple 실패: $it") }
                        }
                        CoupleLinkScreen(
                            myCode = myCode,
                            linking = linking,
                            onBack = { backStack.removeLastOrNull() },
                            onCopyMyCode = { println("[Couple] copy code: $myCode (clipboard TODO)") },
                            onShareMyCode = { println("[Couple] share code: $myCode (share sheet TODO)") },
                            onLink = { partnerCode ->
                                if (linking) return@CoupleLinkScreen
                                linking = true
                                scope.launch {
                                    runCatching { CouplesRepository.joinByInviteCode(partnerCode) }
                                        .onSuccess {
                                            println("[Couple] joined couple $it → 홈으로 이동")
                                            linking = false
                                            goHome()
                                        }
                                        .onFailure {
                                            println("[Couple] join 실패: $it")
                                            linking = false
                                        }
                                }
                            },
                        )
                    }
                    entry<MainRoute> {
                        var scheduleEntries by remember { mutableStateOf(emptyMap<LocalDate, CalendarDayEntry>()) }
                        // MVP: 오늘 기준 ±3개월 스케줄을 한 번에 로드해 chip 으로 노출.
                        // 월 이동 시 재조회 · 실시간 반영은 후속 이슈.
                        LaunchedEffect(Unit) {
                            runCatching { SchedulesRepository.listInRange(oneMonthAgo(), oneMonthAhead()) }
                                .onSuccess { rows -> scheduleEntries = rows.toCalendarEntries() }
                                .onFailure { println("[Schedule] listInRange 실패: $it") }
                        }
                        MainScreen(
                            entries = scheduleEntries,
                            onAddSchedule = { backStack.add(CreateScheduleRoute) },
                        )
                    }
                    entry<CreateScheduleRoute> {
                        var saving by remember { mutableStateOf(false) }
                        CreateScheduleScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onSave = { draft ->
                                if (saving) return@CreateScheduleScreen
                                saving = true
                                scope.launch {
                                    runCatching { SchedulesRepository.create(draft) }
                                        .onSuccess {
                                            println("[Schedule] 저장 성공: $it")
                                            saving = false
                                            backStack.removeLastOrNull()
                                        }
                                        .onFailure {
                                            println("[Schedule] 저장 실패: $it")
                                            saving = false
                                        }
                                }
                            },
                        )
                    }
                },
            )
        }
    }
}
