package com.hyunjine.linker.feature.widget

import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.data.remote.SupabaseProvider
import com.hyunjine.linker.feature.main.resolveOwnerForViewer
import com.hyunjine.linker.feature.main.toKoreanClock
import io.github.jan.supabase.auth.auth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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

@Serializable
data class TodayWidgetPayload(
    /** "yyyy-MM-dd" — 위젯 timeline entry 유효성 판별용. */
    val date: String,
    val items: List<TodayWidgetSchedule>,
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
        return TodayWidgetPayload(date = today.toString(), items = items)
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

    private data class SortableItem(val item: TodayWidgetSchedule, val time: String?) {
        fun sortKey(): String? = time
    }
}
