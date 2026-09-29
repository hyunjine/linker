package com.hyunjine.linker.feature.widget

import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.data.remote.resolveUsCalendarColorId
import com.hyunjine.linker.data.specialday.SpecialDayKind
import com.hyunjine.linker.data.specialday.SpecialDayRepository
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import com.hyunjine.linker.designsystem.theme.toRgbHex
import com.hyunjine.linker.feature.main.resolveOwnerForViewer
import com.hyunjine.linker.feature.main.toKoreanClock
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * iOS 위젯이 App Group 컨테이너에서 읽어 그리는 오늘 일정 payload.
 * Swift 쪽 `WidgetTodayPayload` / `WidgetSchedule` 과 필드 이름을 맞춘다.
 * (Android 위젯은 별도 이슈 — 여기서는 iOS 만 대상.)
 */
@Serializable
data class TodayWidgetSchedule(
    val id: String,
    val title: String,
    /** "오전 10:00" 같이 이미 포맷된 문자열. all-day · task 는 null. */
    @SerialName("timeLabel") val timeLabel: String?,
    /** "me" · "partner" · "us". Swift 가 색으로 매핑. */
    @SerialName("ownerKind") val ownerKind: String,
    /** true = 체크박스 UI. */
    @SerialName("isTask") val isTask: Boolean,
    @SerialName("isDone") val isDone: Boolean,
)

/**
 * 4×2 split 위젯 (#244) 우측 컬럼 전용 미완료 할 일. 시각 · 완료 상태가 필요 없어 필드가 얇다.
 * 지난 날짜의 미완료 항목도 그대로 담기며, [startDate] 가 오늘보다 과거이면 위젯이 "지연" 뱃지로 강조.
 */
@Serializable
data class TodayWidgetOpenTask(
    val id: String,
    val title: String,
    /** "yyyy-MM-dd" — overdue 판정용. */
    @SerialName("startDate") val startDate: String,
    /** "me" · "partner" · "us". 좌측 owner dot 색 결정. */
    @SerialName("ownerKind") val ownerKind: String,
)

@Serializable
data class TodayWidgetPayload(
    /** "yyyy-MM-dd" — 위젯 timeline entry 유효성 판별용. */
    val date: String,
    val items: List<TodayWidgetSchedule>,
    /**
     * 뷰어 관점 소유자별 색상 hex ("#RRGGBB"). 위젯의 OwnerDot 이 [TodayWidgetSchedule.ownerKind]
     * 로 이 세 값 중 하나를 골라 사용. 앱 UI 와 동일한 팔레트 (`calendarColorFor`) 를 넘겨줘야
     * 사용자가 프로필에서 컬러를 바꿔도 위젯에 즉시 반영된다.
     */
    @SerialName("meColorHex") val meColorHex: String,
    @SerialName("partnerColorHex") val partnerColorHex: String,
    @SerialName("usColorHex") val usColorHex: String,
    /**
     * 미완료 할 일 (#244 split 위젯 우측). `type='task' AND is_done=false AND start_date <= today`.
     * 지난 날짜의 미완료 항목이 계속 누적. `oldest first` 로 정렬 (overdue 우선 노출).
     * 기존 today 위젯은 이 필드를 무시하므로 하위호환 안전.
     */
    @SerialName("openTasks") val openTasks: List<TodayWidgetOpenTask> = emptyList(),
    /**
     * 이번 달 (payload.date 가 속한 달) 각 날짜별 이벤트 owner 리스트. 캘린더 위젯 미니 달력의
     * dot indicator 용. key = "yyyy-MM-dd", value = 그 날짜에 있는 이벤트들의 owner 집합
     * (`me` · `partner` · `us`, 중복 제거). 이벤트 없는 날짜는 map 에서 아예 빠짐.
     * 기존 위젯은 이 필드를 무시하므로 하위호환 안전.
     */
    @SerialName("monthEvents") val monthEvents: Map<String, List<String>> = emptyMap(),
    /**
     * 이번 달 공휴일 (`isHoliday="Y"`) 날짜 리스트. "yyyy-MM-dd" ISO 문자열. 캘린더 위젯이
     * 이 날짜 셀 번호를 빨강으로 렌더하는 데 사용 (앱 캘린더 톤과 통일 · #309).
     * 실패 시 빈 리스트 → 위젯은 요일 컬러(토=파랑·일=빨강)만 적용.
     */
    @SerialName("holidays") val holidays: List<String> = emptyList(),
    /**
     * 디데이 앵커 날짜 "yyyy-MM-dd" (#329). null 이면 아직 앵커 미설정 → 위젯이 "설정해주세요" 폴백.
     * 하위 호환용 optional. 기존 위젯은 무시.
     */
    @SerialName("ddayAnchorDate") val ddayAnchorDate: String? = null,
    /**
     * 오늘 기준 다가오는 다음 milestone 라벨 (예: "100일", "1주년") (#329).
     * null 이면 다음 milestone 이 없거나 앵커 미설정 상태 → 위젯 우측 "다음 기념일" 카드가 감춰짐.
     */
    @SerialName("ddayNextMilestoneLabel") val ddayNextMilestoneLabel: String? = null,
    /** 다음 milestone 도래 날짜 "yyyy-MM-dd". */
    @SerialName("ddayNextMilestoneDate") val ddayNextMilestoneDate: String? = null,
    /** 다음 milestone 까지 남은 일수 (오늘 = 0 = D-DAY, 미래 = 양수 → D-N). */
    @SerialName("ddayNextMilestoneDelta") val ddayNextMilestoneDelta: Int? = null,
)

/**
 * 오늘 일정 · 할 일을 위젯 payload 로 빌드해 JSON 으로 직렬화.
 * iOS 앱이 이 문자열을 App Group 파일에 write → WidgetKit reload.
 *
 * 정렬 (#382): 할 일 먼저 → 시각 있는 일정 오름차순 → 종일 일정. 홈 캘린더 · 분할 위젯 (미완료 할 일
 * 우선) 과 잠금화면 · 오늘일정 위젯의 순서를 맞춘다.
 * couple 미가입은 빈 items 로 대응 (위젯이 "일정 없음" 표시).
 *
 * 세션이 준비되지 않았거나 일정 조회가 실패하면 **payload 를 만들지 않는다** (#367). 예전엔 실패를
 * 빈 리스트로 삼켜 멀쩡한 위젯 파일을 "할 일 0개" 로 덮어썼다 — 앱 실행 직후 세션 복원 중이거나
 * 자정 silent push 로 깨어났을 때 토큰이 만료돼 있으면 위젯이 비는 원인.
 */
object TodayWidgetPayloadBuilder {

    private val json = Json { encodeDefaults = true }

    /** 세션 복원 · 갱신 재시도를 기다리는 최대 시간. silent push 실행 한도 (~30초) 안에 끝나도록. */
    private val SessionWaitTimeout = 15.seconds

    /** 이 시간 안에 만료될 토큰은 미리 갱신하고 조회한다. */
    private val TokenExpiryMargin = 60.seconds

    /**
     * 위젯 payload JSON. null 이면 호출 측은 기존 위젯 파일을 **그대로 둬야** 한다
     * (세션 미준비 · 토큰 갱신 실패 · 일정 조회 실패). 로그아웃 상태는 빈 payload 를 돌려줘 위젯을 비운다.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun buildJson(): String? {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val payload = when (awaitSession()) {
            SessionReadiness.Ready -> buildPayload(today) ?: return null
            // 로그아웃: 이전 계정 데이터가 남지 않도록 비운다. 색은 buildPayload 의 기본 fallback 과 동일.
            SessionReadiness.LoggedOut -> TodayWidgetPayload(
                date = today.toString(),
                items = emptyList(),
                meColorHex = calendarColorFor(null).toRgbHex(),
                partnerColorHex = calendarColorFor("pink").toRgbHex(),
                usColorHex = CalendarPurple.toRgbHex(),
            )
            SessionReadiness.Unavailable -> return null
        }
        return json.encodeToString(TodayWidgetPayload.serializer(), payload)
    }

    private enum class SessionReadiness {
        /** 유효한 (만료 전) 세션으로 조회 가능. */
        Ready,

        /** 확실히 로그아웃 — 위젯을 비워도 된다. */
        LoggedOut,

        /** 복원 중 · 갱신 재시도 중 · 갱신 실패 — 이번엔 건너뛰고 기존 위젯 유지. */
        Unavailable,
    }

    /**
     * 세션이 "로그인됨" 또는 "로그아웃" 으로 확정될 때까지 기다린다. 복원 중 (`Initializing`) 이나
     * 갱신 재시도 중 (`RefreshFailure` — 라이브러리가 10초마다 자동 재시도) 이면 그 결과를 기다림.
     * 로그인됨이어도 토큰이 곧 만료되면 먼저 갱신한다 (백그라운드에서 깨어나 자동 갱신 타이머가
     * 못 돈 경우).
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun awaitSession(): SessionReadiness {
        val auth = SupabaseProvider.client.auth
        val status = withTimeoutOrNull(SessionWaitTimeout) {
            auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
        }
        return when (status) {
            is SessionStatus.NotAuthenticated -> SessionReadiness.LoggedOut
            is SessionStatus.Authenticated -> {
                if (status.session.expiresAt > Clock.System.now() + TokenExpiryMargin) {
                    SessionReadiness.Ready
                } else {
                    runCatching { auth.refreshCurrentSession() }
                        .onFailure { println("[Widget] 토큰 갱신 실패 — 기존 위젯 유지: $it") }
                        .fold(onSuccess = { SessionReadiness.Ready }, onFailure = { SessionReadiness.Unavailable })
                }
            }
            else -> {
                println("[Widget] 세션 확정 대기 시간 초과 — 기존 위젯 유지")
                SessionReadiness.Unavailable
            }
        }
    }

    /** 일정 조회가 하나라도 실패하면 null — 호출 측이 기존 위젯을 유지한다. */
    @OptIn(ExperimentalTime::class)
    private suspend fun buildPayload(today: LocalDate): TodayWidgetPayload? {
        val viewerId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        val schedules = runCatching {
            val firstOfMonth = LocalDate(today.year, today.month, 1)
            val lastOfMonth = firstOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
            Triple(
                SchedulesRepository.listInRange(today, today),
                SchedulesRepository.listOpenTasks(today),
                SchedulesRepository.listInRange(firstOfMonth, lastOfMonth),
            )
        }.getOrElse {
            println("[Widget] 일정 조회 실패 — 기존 위젯 유지: $it")
            return null
        }
        // #382: 상대방의 "할 일" 은 위젯 어디에도 노출하지 않는다 (상대방 "일정" 은 그대로).
        val (rows, openTaskRows, monthRows) = schedules.let { (today, open, month) ->
            Triple(
                today.filterNot { it.isPartnerTask(viewerId) },
                open.filterNot { it.isPartnerTask(viewerId) },
                month.filterNot { it.isPartnerTask(viewerId) },
            )
        }
        val items = rows
            // #351: 완료된 할 일은 위젯 리스트에서 제외. 홈 split 위젯의 openTasks 흐름과 동작 통일 —
            // 완료 즉시 잠금/오늘 위젯에서도 사라져야 남아있는 항목 = 실제 할 일. 일정(type='schedule')
            // 은 완료 개념이 없으므로 그대로 통과.
            .filter { !(it.type == "task" && it.isDone) }
            .map { it.toWidgetItem(viewerId) }
            .sortedWith(WidgetItemOrder)
            .map { it.item }
        // #244 split 위젯 우측: 오늘까지의 미완료 할 일 (start_date <= today).
        val openTasks = openTaskRows.map { it.toWidgetOpenTask(viewerId) }
        // 캘린더 위젯 미니 달력용 — 이번 달 (today 가 속한 달) 모든 이벤트를 하루 단위로 그룹핑해
        // owner 리스트를 payload 에 실어준다. Swift 쪽이 각 날짜에 dot 을 렌더.
        val monthEvents = monthRows
            .groupBy { it.startDate }
            .mapValues { (_, rows) ->
                rows.map { resolveOwnerForViewer(it.ownerKind, it.createdBy, viewerId) }.distinct()
            }
        // 이번 달 공휴일 리스트 — 캘린더 위젯의 셀 번호 색상 결정용 (#309).
        // 한 해 전체를 한 번 조회한 뒤 (Repository 캐시 프로세스 라이프사이클 동안 유효)
        // 이번 달로 좁혀 "yyyy-MM-dd" 로 직렬화. 실패해도 빈 리스트로 대응.
        val holidayRepo = SpecialDayRepository()
        val holidays = runCatching { holidayRepo.getYear(today.year, SpecialDayKind.Holiday) }
            .getOrDefault(emptyList())
            .mapNotNull { dto ->
                if (!dto.isHoliday || dto.locdate <= 0) return@mapNotNull null
                val y = dto.locdate / 10000
                val m = (dto.locdate / 100) % 100
                val d = dto.locdate % 100
                if (y != today.year || m != today.month.ordinal + 1) return@mapNotNull null
                // yyyy-MM-dd 로 zero-pad 해서 Swift 쪽 파싱 (동일 포맷) 과 정확히 맞춘다.
                val mm = m.toString().padStart(2, '0')
                val dd = d.toString().padStart(2, '0')
                "$y-$mm-$dd"
            }
            .distinct()
        // 앱 UI 와 동일한 팔레트로 me/partner 컬러 hex 를 계산해 payload 에 실어준다.
        // 실패해도 위젯이 렌더 자체는 되어야 하므로 default (파트너 pink, us purple) fallback.
        val mine = runCatching { UsersRepository.myProfile() }.getOrNull()
        val partner = runCatching { UsersRepository.partnerProfile() }.getOrNull()
        // 디데이 앵커 + 다음 milestone (#329). 커플 미가입 · 앵커 미설정 · 조회 실패 시 null 로 폴백.
        val coupleId = runCatching { com.hyunjine.linker.data.remote.CouplesRepository.myCoupleIdOrNull() }.getOrNull()
        val couple = coupleId?.let { runCatching { com.hyunjine.linker.data.remote.CouplesRepository.getCoupleById(it) }.getOrNull() }
        val anchor = couple?.ddayAnchorDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val next = anchor?.let { nextMilestoneAfter(it, today) }
        return TodayWidgetPayload(
            date = today.toString(),
            items = items,
            meColorHex = calendarColorFor(mine?.calendarColor).toRgbHex(),
            partnerColorHex = calendarColorFor(partner?.calendarColor ?: "pink").toRgbHex(),
            // 공동(Us) 색: 커플 공유값 → 내 프로필 값 (#245) → CalendarPurple 순 fallback (#392).
            usColorHex = resolveUsCalendarColorId(couple?.usCalendarColor, mine?.usCalendarColor)
                ?.let { calendarColorFor(it).toRgbHex() }
                ?: CalendarPurple.toRgbHex(),
            openTasks = openTasks,
            monthEvents = monthEvents,
            holidays = holidays,
            ddayAnchorDate = anchor?.toString(),
            ddayNextMilestoneLabel = next?.first,
            ddayNextMilestoneDate = next?.second?.toString(),
            ddayNextMilestoneDelta = next?.let { today.daysUntil(it.second) },
        )
    }

    /**
     * 오늘 기준 가장 가까운 다음 milestone (deltaDays ≥ 0) 반환. anchor 이후 첫 50일 · 100일 등에서
     * 아직 지나지 않은 것 중 가까운 순서로 첫 항목. 없으면 null.
     *
     * milestone 생성 규칙은 앱 캘린더 로직 (`feature.dday.DdayMilestones`) 과 동일 — N일 = anchor
     * + (N-1) 일, N주년 = anchor + N 년. 여기 위젯 payload 는 다음 도래 milestone 한 개만 필요해
     * `feature/dday` 의존성 회피 위해 로컬로 재구성.
     */
    private fun nextMilestoneAfter(anchor: LocalDate, today: LocalDate): Pair<String, LocalDate>? {
        val days = listOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 2000, 3000, 5000, 10000)
        val all = mutableListOf<Pair<String, LocalDate>>()
        days.forEach { n -> all.add("${n}일" to anchor.plus(n - 1, DateTimeUnit.DAY)) }
        for (y in 1..30) all.add("${y}주년" to anchor.plus(y, DateTimeUnit.YEAR))
        return all.filter { it.second >= today }.minByOrNull { it.second }
    }

    private fun SchedulesRepository.Row.toWidgetItem(viewerId: String?): SortableItem {
        val label = if (type == "task" || allDay) null else startTime.toKoreanClock()
        val resolvedOwner = resolveOwnerForViewer(ownerKind, createdBy, viewerId)
        return SortableItem(
            item = TodayWidgetSchedule(
                id = id,
                title = title,
                timeLabel = label,
                ownerKind = resolvedOwner,
                isTask = type == "task",
                isDone = isDone,
            ),
            // "HH:MM:SS" 문자열 사전순 = 시간 오름차순. null 은 sortedWith nullsLast 로 뒤로.
            time = if (type == "task" || allDay) null else startTime,
        )
    }

    private fun SchedulesRepository.Row.toWidgetOpenTask(viewerId: String?): TodayWidgetOpenTask =
        TodayWidgetOpenTask(
            id = id,
            title = title,
            startDate = startDate,
            ownerKind = resolveOwnerForViewer(ownerKind, createdBy, viewerId),
        )

    /**
     * 정렬용 래퍼.
     *
     * @param item 위젯 항목.
     * @param time 시작 시각 "HH:MM:SS" (사전순 = 시간순). 할 일 · 종일 일정은 null.
     */
    internal data class SortableItem(val item: TodayWidgetSchedule, val time: String?) {
        fun sortKey(): String? = time
    }

    /** 할 일 먼저 → 시각 있는 일정 시간순 → 종일 일정 (#382). 같은 그룹 안에서는 원래 순서 유지 (stable). */
    internal val WidgetItemOrder: Comparator<SortableItem> =
        compareBy<SortableItem> { if (it.item.isTask) 0 else 1 }
            .thenBy(nullsLast()) { it.sortKey() }
}

/**
 * 뷰어 관점에서 상대방 소유의 할 일인지 (#382). 위젯에서는 상대방 할 일만 숨기고 상대방 일정은 보여준다.
 *
 * @param viewerId 현재 로그인 유저 id. null 이면 DB 의 creator 관점 owner 로 판단.
 */
internal fun SchedulesRepository.Row.isPartnerTask(viewerId: String?): Boolean =
    type == "task" && resolveOwnerForViewer(ownerKind, createdBy, viewerId) == "partner"
