package com.hyunjine.linker.feature.widget

import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.data.specialday.SpecialDayKind
import com.hyunjine.linker.data.specialday.SpecialDayRepository
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor
import com.hyunjine.linker.designsystem.theme.toRgbHex
import com.hyunjine.linker.feature.main.resolveOwnerForViewer
import com.hyunjine.linker.feature.main.toKoreanClock
import io.github.jan.supabase.auth.auth
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
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
)

/**
 * 오늘 일정 · 할 일을 위젯 payload 로 빌드해 JSON 으로 직렬화.
 * iOS 앱이 이 문자열을 App Group 파일에 write → WidgetKit reload.
 *
 * 정렬: 시각 있는 항목 오름차순, 그 다음 종일/할 일 순 (안정적 표시 위해).
 * couple 미가입 · 세션 없음 등은 빈 items 로 대응 (위젯이 "일정 없음" 표시).
 */
object TodayWidgetPayloadBuilder {

    private val json = Json { encodeDefaults = true }

    @OptIn(ExperimentalTime::class)
    suspend fun buildJson(): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return json.encodeToString(TodayWidgetPayload.serializer(), buildPayload(today))
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun buildPayload(today: LocalDate): TodayWidgetPayload {
        val viewerId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        val rows = runCatching { SchedulesRepository.listInRange(today, today) }
            .getOrDefault(emptyList())
        val items = rows
            .map { it.toWidgetItem(viewerId) }
            .sortedWith(compareBy(nullsLast()) { it.sortKey() })
            .map { it.item }
        // #244 split 위젯 우측: 오늘까지의 미완료 할 일 (start_date <= today).
        // 실패해도 나머지 위젯은 렌더돼야 하므로 빈 리스트 fallback.
        val openTasks = runCatching { SchedulesRepository.listOpenTasks(today) }
            .getOrDefault(emptyList())
            .map { it.toWidgetOpenTask(viewerId) }
        // 캘린더 위젯 미니 달력용 — 이번 달 (today 가 속한 달) 모든 이벤트를 하루 단위로 그룹핑해
        // owner 리스트를 payload 에 실어준다. Swift 쪽이 각 날짜에 dot 을 렌더.
        val firstOfMonth = LocalDate(today.year, today.month, 1)
        val lastOfMonth = firstOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        val monthEvents = runCatching { SchedulesRepository.listInRange(firstOfMonth, lastOfMonth) }
            .getOrDefault(emptyList())
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
        return TodayWidgetPayload(
            date = today.toString(),
            items = items,
            meColorHex = calendarColorFor(mine?.calendarColor).toRgbHex(),
            partnerColorHex = calendarColorFor(partner?.calendarColor ?: "pink").toRgbHex(),
            usColorHex = CalendarPurple.toRgbHex(),
            openTasks = openTasks,
            monthEvents = monthEvents,
            holidays = holidays,
        )
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

    private data class SortableItem(val item: TodayWidgetSchedule, val time: String?) {
        fun sortKey(): String? = time
    }
}
