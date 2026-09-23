package com.hyunjine.linker.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** 알림 보관 기간. 서버 pg_cron 정리 주기와 맞춘다. */
private val RetentionPeriod = 30.days

/**
 * 알림 내역 화면 (#303) 상태 · 로딩 담당. 진입마다 최근 30일치를 새로 받아 날짜별로 묶는다.
 * 읽음 처리는 없음.
 */
class NotificationsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init { load() }

    /** 최근 30일 알림을 다시 받는다. 화면 전체를 스켈레톤으로 바꾸고, 실패 시 에러 상태로 전환. */
    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            fetch()
                .onSuccess { groups -> _uiState.update { it.copy(loading = false, groups = groups) } }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(loading = false, error = t.message?.takeIf { m -> m.isNotBlank() } ?: "네트워크 오류")
                    }
                }
        }
    }

    /**
     * 당겨서 새로고침 (#365). 기존 리스트를 그대로 둔 채 인디케이터만 띄우고, 실패해도 기존 내역 유지.
     * 이미 새로고침 중이면 무시.
     */
    fun refresh() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            fetch()
                .onSuccess { groups -> _uiState.update { it.copy(refreshing = false, error = null, groups = groups) } }
                .onFailure { _uiState.update { it.copy(refreshing = false) } }
        }
    }

    /** 최근 30일치를 받아 날짜별로 묶는다. 시간 라벨도 호출 시점 기준으로 다시 계산. */
    private suspend fun fetch(): Result<List<NotificationGroup>> {
        val now = Clock.System.now()
        return runCatching { NotificationsRepository.listSince(now - RetentionPeriod) }
            .map { rows ->
                groupByDay(rows.mapNotNull { it.toItemOrNull() }, now, TimeZone.currentSystemDefault())
            }
            .onFailure { t -> println("[Notifications] fetch 실패: $t") }
    }
}

/** 알림 종류. 행 아이콘 · 색을 결정. */
enum class NotificationKind {
    /** 상대방이 일정 · 할 일을 추가 · 수정 · 삭제. */
    Partner,

    /** 일정 · 할 일 시작 리마인더. */
    Reminder,

    /** 관리자 공지. */
    Announcement,

    /** 새 버전 안내. */
    Update,
}

/**
 * 알림 한 건.
 *
 * @param id notifications.id.
 * @param kind 종류.
 * @param title 제목.
 * @param body 본문.
 * @param receivedAt 받은 시각.
 */
data class NotificationItem(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val receivedAt: Instant,
)

/**
 * 같은 날 받은 알림 묶음.
 *
 * @param label 그룹 라벨 — `오늘` · `어제` · `9월 20일 (토)`.
 * @param items 최신순 알림.
 * @param timeLabels [items] 와 같은 순서의 우측 시간 라벨.
 */
data class NotificationGroup(
    val label: String,
    val items: List<NotificationItem>,
    val timeLabels: List<String>,
)

/**
 * @param loading 최초 로딩 중 (스켈레톤).
 * @param refreshing 당겨서 새로고침 중 (리스트 유지 + 인디케이터).
 * @param error 로딩 실패 메시지. null 이면 정상.
 * @param groups 날짜별 묶음 (최신 날짜 먼저).
 */
data class NotificationsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val groups: List<NotificationGroup> = emptyList(),
)

/** 서버 row → 화면 모델. 알 수 없는 kind · 깨진 시각은 건너뛴다. */
private fun NotificationsRepository.Row.toItemOrNull(): NotificationItem? {
    val kind = when (kind) {
        "partner" -> NotificationKind.Partner
        "reminder" -> NotificationKind.Reminder
        "announcement" -> NotificationKind.Announcement
        "update" -> NotificationKind.Update
        else -> return null
    }
    val at = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return null
    return NotificationItem(id = id, kind = kind, title = title, body = body, receivedAt = at)
}

/**
 * 받은 날짜 (로컬) 별로 묶고, 각 알림의 시간 라벨을 만든다.
 *
 * @param items 알림 (순서 무관).
 * @param now 기준 시각.
 * @param zone 날짜 경계를 가를 타임존.
 */
internal fun groupByDay(items: List<NotificationItem>, now: Instant, zone: TimeZone): List<NotificationGroup> {
    val today = now.toLocalDateTime(zone).date
    return items
        .sortedByDescending { it.receivedAt }
        .groupBy { it.receivedAt.toLocalDateTime(zone).date }
        .map { (date, dayItems) ->
            NotificationGroup(
                label = dayLabel(date, today),
                items = dayItems,
                timeLabels = dayItems.map { timeLabel(it.receivedAt, now, zone, isToday = date == today) },
            )
        }
}

/**
 * 그룹 라벨. 오늘 · 어제는 단어, 그 외는 `9월 20일 (토)`.
 *
 * @param date 그룹 날짜.
 * @param today 오늘.
 */
internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.minus(1, DateTimeUnit.DAY) -> "어제"
    else -> "${date.month.ordinal + 1}월 ${date.day}일 (${date.dayOfWeek.koreanShort()})"
}

/**
 * 행 우측 시간 라벨. 오늘 받은 건 상대 시간 (`방금` · `10분 전` · `2시간 전`),
 * 그 이전은 날짜가 그룹 라벨에 있으므로 시각만 (`오후 8:40`).
 *
 * @param at 받은 시각.
 * @param now 기준 시각.
 * @param zone 시각 표기용 타임존.
 * @param isToday 오늘 그룹 여부.
 */
internal fun timeLabel(at: Instant, now: Instant, zone: TimeZone, isToday: Boolean): String {
    if (isToday) {
        val minutes = (now - at).inWholeMinutes.coerceAtLeast(0)
        return when {
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes}분 전"
            else -> "${minutes / 60}시간 전"
        }
    }
    val t = at.toLocalDateTime(zone)
    val ampm = if (t.hour < 12) "오전" else "오후"
    val h12 = when {
        t.hour == 0 -> 12
        t.hour > 12 -> t.hour - 12
        else -> t.hour
    }
    return "$ampm $h12:${t.minute.toString().padStart(2, '0')}"
}

private fun DayOfWeek.koreanShort(): String = when (this) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}
