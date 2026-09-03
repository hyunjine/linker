package com.hyunjine.linker.feature.reminder

import com.hyunjine.linker.data.local.RemindersLocal
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.platform.LocalNotifications
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 로컬 알림 예약을 총괄. OS 는 등록된 pending 알림 목록만 알지 우리가 무엇을 예약했는지 세션-투-세션
 * 으로 안전하게 알지 못하므로, 상위에서 "지난 예약 id 세트" 를 [RemindersLocal] 에 캐시해두고
 * 재-scheduling 시 하나씩 cancel + 새로 schedule 한다.
 *
 * 스코프:
 *  - 시간 있는 일정 (`type=schedule` + `all_day=false` + `start_time != null`) 만 예약.
 *  - 종일 · 할 일은 이번 스코프 밖 (기본 아침 알림 정책 등은 후속 이슈).
 *  - 앞으로 [HORIZON_DAYS] 일 (기본 30일) 안의 인스턴스만. 반복 시리즈로 만들어진 미래 인스턴스도
 *    자연스레 이 범위에 잡히므로 별도 처리 불필요.
 *  - iOS 는 앱당 64개 pending notification 제한이 있어 우리도 상한을 그 안쪽으로 (기본 60).
 *
 * 재-scheduling 트리거:
 *  - 스케줄 CRUD 성공 후 (VM 이 호출)
 *  - 앱 foreground 진입 시 (선택)
 *  - Realtime 이벤트 도착 시 (파트너 변경 반영)
 */
object ReminderScheduler {
    private const val HORIZON_DAYS: Int = 30
    private const val MAX_PENDING: Int = 60

    /**
     * "다가올 알림" 재구성. 현재 예약된 id 를 모두 취소하고, 지금부터 [HORIZON_DAYS] 사이의 시간 있는
     * 일정들만 시작 시각으로 다시 예약. 실패는 삼키고 로그만 (알림은 백그라운드 편의 기능이라 UI 를
     * 막지 않는다).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun rebuild() {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val startDate = now.toLocalDateTime(tz).date
        val endDate = startDate.plus(HORIZON_DAYS, DateTimeUnit.DAY)

        val rows = runCatching { SchedulesRepository.listInRange(startDate, endDate) }
            .onFailure { println("[Reminder] listInRange 실패: $it") }
            .getOrDefault(emptyList())

        // 시간 있는 일정만 · 미래 시각만 · 시작 오름차순 정렬 · 상한 컷.
        val candidates = rows.mapNotNull { row -> row.toReminder(tz) }
            .filter { it.epochSeconds > (now.epochSeconds + 5) }  // 5초 슬랙: race 방지
            .sortedBy { it.epochSeconds }
            .take(MAX_PENDING)

        val previousIds = RemindersLocal.loadIds()
        previousIds.forEach { LocalNotifications.cancel(it) }

        val newIds = mutableSetOf<String>()
        for (r in candidates) {
            LocalNotifications.schedule(
                id = r.id,
                title = r.title,
                body = r.body,
                epochSeconds = r.epochSeconds,
            )
            newIds += r.id
        }
        RemindersLocal.saveIds(newIds)
        println("[Reminder] rebuilt — scheduled ${newIds.size} / candidates ${candidates.size}")
    }

    /** 특정 id 하나만 취소 (Bulk 필요 없으면 CRUD 후 [rebuild] 로 통일해도 됨). */
    fun cancelOne(id: String) {
        LocalNotifications.cancel(id)
        RemindersLocal.remove(id)
    }
}

/** ReminderScheduler 내부에서만 쓰는 candidate 표현. */
private data class ReminderItem(
    val id: String,
    val title: String,
    val body: String,
    val epochSeconds: Long,
)

/** DB row → ReminderItem. 예약 대상 아니면 null (시간 없음 · 종일 · 할 일 · 파싱 실패). */
@OptIn(ExperimentalTime::class)
private fun SchedulesRepository.Row.toReminder(tz: TimeZone): ReminderItem? {
    if (type != "schedule") return null           // 할 일은 스킵
    if (allDay) return null                       // 종일은 스킵
    val hhmmss = startTime ?: return null         // "HH:MM:SS"
    val parts = hhmmss.split(':')
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null

    val date = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
    val dt = LocalDateTime(date, LocalTime(hour, minute))
    val epoch = dt.toInstant(tz).epochSeconds

    val timeLabel = "${if (hour < 12) "오전" else "오후"} " +
        "${if (hour == 0) 12 else if (hour > 12) hour - 12 else hour}:${minute.toString().padStart(2, '0')}"
    return ReminderItem(
        id = id,
        title = title.ifBlank { "일정" },
        body = "$timeLabel 시작",
        epochSeconds = epoch,
    )
}
