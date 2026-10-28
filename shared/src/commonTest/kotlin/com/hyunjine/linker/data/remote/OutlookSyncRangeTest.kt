package com.hyunjine.linker.data.remote

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OutlookSyncRangeTest {

    @Test
    fun defaultRangeIsSixtyOneDaysBackToNinetyThreeDaysAhead() {
        assertEquals(
            LocalDate(2026, 8, 28) to LocalDate(2027, 1, 29),
            OutlookSyncService.defaultRange(LocalDate(2026, 10, 28)),
        )
    }

    @Test
    fun defaultRangeCrossesLeapDay() {
        assertEquals(
            LocalDate(2027, 12, 30) to LocalDate(2028, 6, 1),
            OutlookSyncService.defaultRange(LocalDate(2028, 2, 29)),
        )
    }
}
