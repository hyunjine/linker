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
import com.hyunjine.linker.auth.rememberKakaoLoginClient
import com.hyunjine.linker.auth.sessionStatus
import com.hyunjine.linker.auth.signInWithKakao
import com.hyunjine.linker.auth.signOut
import com.hyunjine.linker.data.remote.AnniversariesRepository
import com.hyunjine.linker.data.remote.CouplesRepository
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.ui.anniversary.AnniversariesScreen
import com.hyunjine.linker.ui.anniversary.AnniversaryUi
import com.hyunjine.linker.ui.couple.CoupleInviteCodeScreen
import com.hyunjine.linker.ui.couple.CoupleJoinScreen
import com.hyunjine.linker.ui.couple.CoupleLinkScreen
import com.hyunjine.linker.ui.login.LoginScreen
import com.hyunjine.linker.ui.main.AllDaySchedule
import com.hyunjine.linker.ui.main.CalendarDayEntry
import com.hyunjine.linker.ui.main.CalendarEvent
import com.hyunjine.linker.ui.main.CalendarEventType
import com.hyunjine.linker.ui.main.DayDetail
import com.hyunjine.linker.ui.main.DayOwner
import com.hyunjine.linker.ui.main.DayTask
import com.hyunjine.linker.ui.main.MainScreen
import com.hyunjine.linker.ui.main.TimedSchedule
import com.hyunjine.linker.ui.profile.ProfileSetupScreen
import com.hyunjine.linker.ui.schedule.CreateScheduleScreen
import com.hyunjine.linker.ui.search.SearchAnniversaryItem
import com.hyunjine.linker.ui.search.SearchResults
import com.hyunjine.linker.ui.search.SearchScheduleItem
import com.hyunjine.linker.ui.search.SearchScreen
import com.hyunjine.linker.ui.splash.SplashScreen
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.graphics.Color
import com.hyunjine.linker.platform.rememberCopyToClipboard
import com.hyunjine.linker.platform.rememberShareText
import com.hyunjine.linker.ui.theme.CalendarPurple
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.calendarColorFor
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
private data object SplashRoute : NavKey

@Serializable
private data object LoginRoute : NavKey

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
 */
@Serializable
private data class CreateScheduleRoute(val scheduleId: String? = null) : NavKey

@Serializable
private data object AnniversariesRoute : NavKey

@Serializable
private data object SearchRoute : NavKey

private val NavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SplashRoute::class, SplashRoute.serializer())
            subclass(LoginRoute::class, LoginRoute.serializer())
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

/** owner_kind → chip 색상. 프로필 로드 전엔 [Default] 폴백. */
private data class OwnerColors(val me: Color, val partner: Color, val us: Color) {
    fun forOwner(ownerKind: String): Color = when (ownerKind) {
        "me" -> me
        "partner" -> partner
        else -> us
    }

    companion object {
        val Default = OwnerColors(
            me = calendarColorFor("blue"),
            partner = calendarColorFor("pink"),
            us = CalendarPurple,
        )
    }
}

/**
 * 서버에서 받은 스케줄 rows 를 MainScreen 이 소비하는 `Map<LocalDate, CalendarDayEntry>` 로 변환.
 * 스케줄이 [start_date, end_date] 범위를 커버하면 각 날짜에 chip 을 추가한다.
 * chip 색은 [ownerColors] 로 결정 (me/partner/us).
 */
private fun List<SchedulesRepository.Row>.toCalendarEntries(
    ownerColors: OwnerColors,
): Map<LocalDate, CalendarDayEntry> {
    val out = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    for (row in this) {
        val start = LocalDate.parse(row.startDate)
        val end = LocalDate.parse(row.endDate)
        val tint = ownerColors.forOwner(row.ownerKind)
        var d = start
        while (d <= end) {
            out.getOrPut(d) { mutableListOf() }
                .add(CalendarEvent(row.title, CalendarEventType.Personal, tintColor = tint, id = row.id))
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return out.mapValues { CalendarDayEntry(events = it.value.toList()) }
}

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
    val d = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}. $m. $d."
}

/** ISO date (yyyy-MM-dd) → 드로워 핸들 자리에 표시할 "yyyy.MM.dd" (점 사이 공백 없음). */
private fun isoToHandleBirthDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.dayOfMonth.toString().padStart(2, '0')
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
    MaterialTheme {
        ProvidePretendard {
            val backStack = rememberNavBackStack(NavConfig, SplashRoute)
            val scope = rememberCoroutineScope()
            // 온보딩 완료(커플 연결) 시점에 로그인·프로필·연결 스택을 전부 비우고 Main 만 남긴다.
            // 홈에서 뒤로가기로 로그인 화면이 다시 뜨면 안 되므로 clear + push 조합.
            val goHome: () -> Unit = {
                backStack.clear()
                backStack.add(MainRoute)
            }
            // 프로필 편집 후 홈 드로워가 최신 값으로 다시 로드되게끔 하는 신호.
            // ProfileEditRoute 저장 성공 시 bump → MainRoute LaunchedEffect 가 재실행.
            var profileRefreshTick by remember { mutableStateOf(0) }

            // 세션 상태 기반 부트스트랩 라우팅.
            //  - Initializing: SplashRoute 유지 (세션 storage 로드 중)
            //  - Authenticated: 프로필/커플 상태 조회 → 미완성 단계로 자동 진입 (재로그인 시 온보딩 스킵)
            //  - NotAuthenticated / RefreshFailure: 로그인 화면으로 스택 초기화
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
                    is SessionStatus.Initializing -> Unit  // SplashRoute 그대로 유지
                }
            }

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<SplashRoute> { SplashScreen() }
                    entry<LoginRoute> {
                        // rememberKakaoLoginClient 는 LocalContext (Android) / LocalUIViewController (iOS)
                        // 를 참조하므로 Composable 스코프 안에서 얻어야 한다.
                        val kakao = rememberKakaoLoginClient()
                        LoginScreen(
                            onKakaoLoginClick = {
                                println("[Auth] 카카오 버튼 click")
                                scope.launch {
                                    runCatching { signInWithKakao(kakao) }
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
                            defaultAvatarUrl = defaults.avatarUrl.toSecureImageUrl(),
                            saving = saving,
                            onBack = { backStack.removeLastOrNull() },
                            onNext = { nickname, birthDate, colorId ->
                                saving = true
                                scope.launch {
                                    runCatching {
                                        UsersRepository.completeProfile(
                                            nickname = nickname,
                                            birthDate = birthDate,
                                            profileImageUrl = defaults.avatarUrl.toSecureImageUrl(),
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
                    entry<ProfileEditRoute> {
                        // 프리필 로드 전에는 화면을 mount 하지 않는다 (ProfileSetupScreen 이 초기값을
                        // rememberSaveable 로 잡아버려 나중에 nickname 파라미터가 바뀌어도 반영 안 됨).
                        var profile by remember { mutableStateOf<UsersRepository.Profile?>(null) }
                        var loaded by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            profile = runCatching { UsersRepository.myProfile() }
                                .onFailure { println("[ProfileEdit] 프로필 로드 실패: $it") }
                                .getOrNull()
                            loaded = true
                        }
                        if (!loaded) return@entry
                        val p = profile ?: run {
                            println("[ProfileEdit] 프로필 없음 — 편집 화면 진입 취소")
                            backStack.removeLastOrNull()
                            return@entry
                        }
                        var saving by remember { mutableStateOf(false) }
                        ProfileSetupScreen(
                            nickname = p.nickname ?: "",
                            birthDate = p.birthDate?.let(::isoToDisplayBirthDate) ?: "2000. 01. 01.",
                            selectedColorId = p.calendarColor,
                            defaultAvatarUrl = p.profileImageUrl.toSecureImageUrl(),
                            submitText = "저장",
                            saving = saving,
                            onBack = { backStack.removeLastOrNull() },
                            onNext = { nickname, birthDate, colorId ->
                                if (saving) return@ProfileSetupScreen
                                saving = true
                                scope.launch {
                                    runCatching {
                                        UsersRepository.updateProfile(
                                            nickname = nickname,
                                            birthDate = birthDate,
                                            calendarColor = colorId,
                                        )
                                    }.onSuccess {
                                        println("[ProfileEdit] 저장 성공")
                                        saving = false
                                        profileRefreshTick++
                                        backStack.removeLastOrNull()
                                    }.onFailure {
                                        println("[ProfileEdit] 저장 실패: $it")
                                        saving = false
                                    }
                                }
                            },
                        )
                    }
                    entry<CoupleLinkRoute> {
                        CoupleLinkScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onCreateInvite = { backStack.add(CoupleInviteCodeRoute) },
                            onEnterPartnerCode = { backStack.add(CoupleJoinRoute) },
                        )
                    }
                    entry<CoupleInviteCodeRoute> {
                        var myCode by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            runCatching { CouplesRepository.createOrGetMyCouple() }
                                .onSuccess {
                                    println("[Couple] my couple id=${it.id} code=${it.inviteCode}")
                                    myCode = it.inviteCode
                                }
                                .onFailure { println("[Couple] createOrGetMyCouple 실패: $it") }
                        }
                        val copyToClipboard = rememberCopyToClipboard()
                        val shareText = rememberShareText()
                        CoupleInviteCodeScreen(
                            myCode = myCode,
                            onBack = { backStack.removeLastOrNull() },
                            onCopy = {
                                val code = myCode ?: return@CoupleInviteCodeScreen
                                copyToClipboard(code)
                                println("[Couple] 클립보드 복사: $code")
                            },
                            onShare = {
                                val code = myCode ?: return@CoupleInviteCodeScreen
                                shareText("링커 초대코드: $code")
                                println("[Couple] 공유 시트 오픈: $code")
                            },
                        )
                    }
                    entry<CoupleJoinRoute> {
                        var linking by remember { mutableStateOf(false) }
                        CoupleJoinScreen(
                            linking = linking,
                            onBack = { backStack.removeLastOrNull() },
                            onLink = { partnerCode ->
                                if (linking) return@CoupleJoinScreen
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
                        val kakao = rememberKakaoLoginClient()
                        // 내 · 파트너 프로필의 calendar_color 로 owner 별 chip 색을 정한다.
                        // 로드 전엔 fallback 팔레트 (blue/pink) 사용.
                        var ownerColors by remember { mutableStateOf(OwnerColors.Default) }
                        var myProfile by remember { mutableStateOf<UsersRepository.Profile?>(null) }
                        // profileRefreshTick 이 바뀌면 재조회 (프로필 편집 후 최신 값 반영).
                        LaunchedEffect(profileRefreshTick) {
                            val mine = runCatching { UsersRepository.myProfile() }
                                .onFailure { println("[Main] myProfile 실패: $it") }
                                .getOrNull()
                            val partner = runCatching { UsersRepository.partnerProfile()?.calendarColor }.getOrNull()
                            myProfile = mine
                            ownerColors = OwnerColors(
                                me = calendarColorFor(mine?.calendarColor),
                                partner = calendarColorFor(partner ?: "pink"),
                                us = CalendarPurple,
                            )
                        }
                        MainScreen(
                            // 월 이동 시마다 호출됨. 캐시는 화면 내부 (MainScreen) 에서 관리.
                            // 범위는 해당 달 ± 1주 (그리드가 인접 월 leading/trailing 셀도 표시).
                            onLoadEntriesForMonth = { yearMonth ->
                                val first = LocalDate(yearMonth.year, yearMonth.month, 1)
                                val from = first.plus(-7, DateTimeUnit.DAY)
                                val to = first.plus(1, DateTimeUnit.MONTH).plus(7, DateTimeUnit.DAY)
                                runCatching { SchedulesRepository.listInRange(from, to) }
                                    .onFailure { println("[Schedule] listInRange($yearMonth) 실패: $it") }
                                    .getOrDefault(emptyList())
                                    .toCalendarEntries(ownerColors)
                            },
                            onLoadDayDetail = { date ->
                                val rows = runCatching { SchedulesRepository.listInRange(date, date) }
                                    .onFailure { println("[Schedule] loadDayDetail($date) 실패: $it") }
                                    .getOrDefault(emptyList())
                                rows.toDayDetail(date)
                            },
                            onToggleTaskDone = { id, done ->
                                runCatching { SchedulesRepository.setTaskDone(id, done) }
                                    .onFailure { println("[Schedule] setTaskDone 실패: $it") }
                            },
                            onAddSchedule = { backStack.add(CreateScheduleRoute()) },
                            onEditSchedule = { id -> backStack.add(CreateScheduleRoute(id)) },
                            onAnniversaryClick = { backStack.add(AnniversariesRoute) },
                            onSearchClick = { backStack.add(SearchRoute) },
                            onProfileEditClick = { backStack.add(ProfileEditRoute) },
                            profileName = myProfile?.nickname.orEmpty(),
                            profileHandle = isoToHandleBirthDate(myProfile?.birthDate),
                            profileImageUrl = myProfile?.profileImageUrl.toSecureImageUrl(),
                            // ownerColors 자체를 invalidation key 로 사용. profileRefreshTick 을
                            // 넘기면 App 의 profile fetch 와 MainScreen 의 chip fetch 가 race 해서
                            // 옛 색으로 tint 된 결과가 캐시에 굳어버림. ownerColors 는 App fetch
                            // 완료 뒤에 갱신되므로, 이걸 key 로 쓰면 새 색이 확정된 이후에만 재fetch.
                            refreshTick = ownerColors.hashCode(),
                            onLogout = {
                                scope.launch {
                                    runCatching { signOut(kakao) }
                                        .onFailure { println("[Auth] signOut 실패: $it") }
                                }
                            },
                        )
                    }
                    entry<SearchRoute> {
                        // 결과 chip 색 계산용 — MainRoute 와 동일 규칙, 화면 진입마다 재조회.
                        var ownerColors by remember { mutableStateOf(OwnerColors.Default) }
                        LaunchedEffect(Unit) {
                            val my = runCatching { UsersRepository.myProfile()?.calendarColor }.getOrNull()
                            val partner = runCatching { UsersRepository.partnerProfile()?.calendarColor }.getOrNull()
                            ownerColors = OwnerColors(
                                me = calendarColorFor(my),
                                partner = calendarColorFor(partner ?: "pink"),
                                us = CalendarPurple,
                            )
                        }
                        SearchScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onSearch = { query ->
                                val schedules = runCatching { SchedulesRepository.search(query) }
                                    .onFailure { println("[Search] schedules 실패: $it") }
                                    .getOrDefault(emptyList())
                                    .map { row ->
                                        SearchScheduleItem(
                                            id = row.id,
                                            title = row.title,
                                            date = LocalDate.parse(row.startDate),
                                            ownerColor = ownerColors.forOwner(row.ownerKind),
                                        )
                                    }
                                val anniversaries = runCatching { AnniversariesRepository.search(query) }
                                    .onFailure { println("[Search] anniversaries 실패: $it") }
                                    .getOrDefault(emptyList())
                                    .map { row ->
                                        SearchAnniversaryItem(
                                            id = row.id,
                                            title = row.title,
                                            date = LocalDate.parse(row.date),
                                            repeatYearly = row.repeatYearly,
                                        )
                                    }
                                SearchResults(schedules = schedules, anniversaries = anniversaries)
                            },
                            onScheduleClick = { id -> backStack.add(CreateScheduleRoute(id)) },
                            onAnniversaryClick = { _ -> backStack.add(AnniversariesRoute) },
                        )
                    }
                    entry<AnniversariesRoute> {
                        var items by remember { mutableStateOf(emptyList<AnniversaryUi>()) }
                        var busy by remember { mutableStateOf(false) }
                        var reloadTick by remember { mutableStateOf(0) }
                        LaunchedEffect(reloadTick) {
                            runCatching { AnniversariesRepository.list() }
                                .onSuccess { rows -> items = rows.map { it.toUi() } }
                                .onFailure { println("[Anniv] list 실패: $it") }
                        }
                        AnniversariesScreen(
                            items = items,
                            busy = busy,
                            onBack = { backStack.removeLastOrNull() },
                            onAdd = { title, date, repeatYearly ->
                                if (busy) return@AnniversariesScreen
                                busy = true
                                scope.launch {
                                    runCatching { AnniversariesRepository.create(title, date, repeatYearly) }
                                        .onSuccess {
                                            println("[Anniv] 저장 성공: $it")
                                            busy = false
                                            reloadTick++
                                        }
                                        .onFailure {
                                            println("[Anniv] 저장 실패: $it")
                                            busy = false
                                        }
                                }
                            },
                            onDelete = { id ->
                                if (busy) return@AnniversariesScreen
                                busy = true
                                scope.launch {
                                    runCatching { AnniversariesRepository.delete(id) }
                                        .onSuccess {
                                            println("[Anniv] 삭제 성공: $id")
                                            busy = false
                                            reloadTick++
                                        }
                                        .onFailure {
                                            println("[Anniv] 삭제 실패: $it")
                                            busy = false
                                        }
                                }
                            },
                        )
                    }
                    entry<CreateScheduleRoute> { route ->
                        val editing = route.scheduleId != null
                        var saving by remember { mutableStateOf(false) }
                        var initial by remember { mutableStateOf<com.hyunjine.linker.ui.schedule.ScheduleDraft?>(null) }
                        var loaded by remember { mutableStateOf(!editing) }
                        LaunchedEffect(route.scheduleId) {
                            if (route.scheduleId == null) return@LaunchedEffect
                            runCatching { SchedulesRepository.getDraftById(route.scheduleId) }
                                .onSuccess {
                                    initial = it
                                    loaded = true
                                }
                                .onFailure {
                                    println("[Schedule] getDraftById 실패: $it")
                                    loaded = true
                                }
                        }
                        // 로드 완료 전에는 화면을 안 그린다 (편집 대상 draft 확정 후 한 번만 mount).
                        if (!loaded) return@entry
                        CreateScheduleScreen(
                            initial = initial,
                            editing = editing,
                            onBack = { backStack.removeLastOrNull() },
                            onSave = { draft ->
                                if (saving) return@CreateScheduleScreen
                                saving = true
                                scope.launch {
                                    val op = if (route.scheduleId != null) {
                                        runCatching { SchedulesRepository.update(route.scheduleId, draft) }
                                    } else {
                                        runCatching { SchedulesRepository.create(draft) }
                                    }
                                    op.onSuccess {
                                        println("[Schedule] ${if (editing) "수정" else "저장"} 성공")
                                        saving = false
                                        backStack.removeLastOrNull()
                                    }.onFailure {
                                        println("[Schedule] ${if (editing) "수정" else "저장"} 실패: $it")
                                        saving = false
                                    }
                                }
                            },
                            onDelete = {
                                val id = route.scheduleId ?: return@CreateScheduleScreen
                                if (saving) return@CreateScheduleScreen
                                saving = true
                                scope.launch {
                                    runCatching { SchedulesRepository.delete(id) }
                                        .onSuccess {
                                            println("[Schedule] 삭제 성공: $id")
                                            saving = false
                                            backStack.removeLastOrNull()
                                        }
                                        .onFailure {
                                            println("[Schedule] 삭제 실패: $it")
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
