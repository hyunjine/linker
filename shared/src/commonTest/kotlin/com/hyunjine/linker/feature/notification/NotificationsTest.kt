package com.hyunjine.linker.feature.notification

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationsTest {

    private val zone = TimeZone.of("Asia/Seoul")

    /** 2026-09-23 15:00 KST. */
    private val now = Instant.parse("2026-09-23T06:00:00Z")

    private fun item(id: String, at: Instant) =
        NotificationItem(id, NotificationKind.Reminder, "t", "b", at)

    @Test
    fun day_label_uses_words_then_date_with_weekday() {
        val today = LocalDate(2026, 9, 23)
        assertEquals("오늘", dayLabel(today, today))
        assertEquals("어제", dayLabel(LocalDate(2026, 9, 22), today))
        assertEquals("9월 20일 (일)", dayLabel(LocalDate(2026, 9, 20), today))
    }

    @Test
    fun today_time_label_is_relative() {
        assertEquals("방금", timeLabel(now - 30.seconds, now, zone, isToday = true))
        assertEquals("10분 전", timeLabel(now - 10.minutes, now, zone, isToday = true))
        assertEquals("2시간 전", timeLabel(now - 150.minutes, now, zone, isToday = true))
    }

    @Test
    fun older_time_label_is_clock() {
        // 2026-09-22 20:40 KST
        assertEquals("오후 8:40", timeLabel(Instant.parse("2026-09-22T11:40:00Z"), now, zone, isToday = false))
        // 2026-09-22 00:05 KST
        assertEquals("오전 12:05", timeLabel(Instant.parse("2026-09-21T15:05:00Z"), now, zone, isToday = false))
    }

    @Test
    fun groups_by_local_day_newest_first() {
        val groups = groupByDay(
            listOf(
                item("old", now - 72.hours),
                item("today", now - 1.hours),
                // 2026-09-23 00:30 KST — UTC 로는 22일이지만 로컬 기준 오늘.
                item("midnight", Instant.parse("2026-09-22T15:30:00Z")),
                item("yesterday", now - 20.hours),
            ),
            now,
            zone,
        )
        assertEquals(listOf("오늘", "어제", "9월 20일 (일)"), groups.map { it.label })
        assertEquals(listOf("today", "midnight"), groups[0].items.map { it.id })
        assertEquals(listOf("1시간 전", "14시간 전"), groups[0].timeLabels)
    }
}
