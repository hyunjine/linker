package com.hyunjine.linker.feature.widget

import com.hyunjine.linker.data.remote.SchedulesRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetPartnerTaskFilterTest {

    private val me = "me-id"
    private val partner = "partner-id"

    private fun row(type: String, ownerKind: String, createdBy: String) = SchedulesRepository.Row(
        id = "id",
        coupleId = "couple",
        createdBy = createdBy,
        type = type,
        ownerKind = ownerKind,
        title = "t",
        startDate = "2026-09-28",
        endDate = "2026-09-28",
        allDay = true,
        isDone = false,
    )

    @Test
    fun partner_task_is_hidden() {
        // 파트너가 자기 것으로 만든 할 일 → 내 관점 partner
        assertTrue(row("task", "me", partner).isPartnerTask(me))
        // 내가 파트너 것으로 만든 할 일 (legacy) → 내 관점 partner
        assertTrue(row("task", "partner", me).isPartnerTask(me))
    }

    @Test
    fun my_and_shared_tasks_are_kept() {
        assertFalse(row("task", "me", me).isPartnerTask(me))
        assertFalse(row("task", "us", partner).isPartnerTask(me))
        // 파트너가 나에게 준 할 일 → 내 관점 me
        assertFalse(row("task", "partner", partner).isPartnerTask(me))
    }

    @Test
    fun partner_schedules_are_kept() {
        assertFalse(row("schedule", "me", partner).isPartnerTask(me))
        assertFalse(row("schedule", "partner", me).isPartnerTask(me))
    }
}

class WidgetItemOrderTest {

    private fun item(id: String, isTask: Boolean, time: String?) = TodayWidgetPayloadBuilder.SortableItem(
        item = TodayWidgetSchedule(id = id, title = id, timeLabel = null, ownerKind = "me", isTask = isTask, isDone = false),
        time = time,
    )

    @Test
    fun tasks_first_then_timed_then_all_day() {
        val sorted = listOf(
            item("allday", isTask = false, time = null),
            item("evening", isTask = false, time = "19:00:00"),
            item("task1", isTask = true, time = null),
            item("morning", isTask = false, time = "09:30:00"),
            item("task2", isTask = true, time = null),
        ).sortedWith(TodayWidgetPayloadBuilder.WidgetItemOrder).map { it.item.id }
        kotlin.test.assertEquals(listOf("task1", "task2", "morning", "evening", "allday"), sorted)
    }
}
