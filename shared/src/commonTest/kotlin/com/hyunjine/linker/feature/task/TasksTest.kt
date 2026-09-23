package com.hyunjine.linker.feature.task

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TasksTest {

    private val today = LocalDate(2026, 9, 23)

    @Test
    fun date_label_uses_words_near_today() {
        assertEquals("오늘", dateLabel(today, today))
        assertEquals("어제", dateLabel(LocalDate(2026, 9, 22), today))
        assertEquals("내일", dateLabel(LocalDate(2026, 9, 24), today))
    }

    @Test
    fun date_label_formats_same_and_other_year() {
        assertEquals("9.21", dateLabel(LocalDate(2026, 9, 21), today))
        assertEquals("5.11", dateLabel(LocalDate(2026, 5, 11), today))
        assertEquals("25.12.11", dateLabel(LocalDate(2025, 12, 11), today))
        assertEquals("09.1.5", dateLabel(LocalDate(2009, 1, 5), today))
    }

    @Test
    fun visible_tasks_filters_by_tab_and_sorts() {
        val tasks = listOf(
            TaskItem("a", "A", LocalDate(2026, 9, 1), isDone = false),
            TaskItem("b", "B", LocalDate(2026, 9, 20), isDone = false),
            TaskItem("c", "C", LocalDate(2026, 9, 10), isDone = true),
        )
        val newest = TasksUiState(loading = false, tasks = tasks)
        assertEquals(listOf("b", "a"), newest.visibleTasks.map { it.id })
        assertEquals(listOf("a", "b"), newest.copy(sort = TaskSort.Oldest).visibleTasks.map { it.id })
        assertEquals(listOf("c"), newest.copy(tab = TaskTab.Done).visibleTasks.map { it.id })
    }
}
