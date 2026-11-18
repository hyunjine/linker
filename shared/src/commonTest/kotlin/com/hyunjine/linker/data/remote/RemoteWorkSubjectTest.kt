package com.hyunjine.linker.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteWorkSubjectTest {

    @Test
    fun parsesStandardFormat() {
        assertEquals(
            RemoteWorkSubject.Parsed("현진", "09:00", "18:00"),
            RemoteWorkSubject.parse("재택(현진) 09:00-18:00"),
        )
    }

    @Test
    fun parsesLooseSpacingSingleDigitHourAndTilde() {
        assertEquals(
            RemoteWorkSubject.Parsed("현진", "09:30", "18:30"),
            RemoteWorkSubject.parse("  재택 ( 현진 ) 9:30 ~ 18:30 (오후 반차)"),
        )
    }

    @Test
    fun ignoresOtherEvents() {
        assertNull(RemoteWorkSubject.parse("주간회의"))
        assertNull(RemoteWorkSubject.parse("재택 근무"))
        assertNull(RemoteWorkSubject.parse("출장(현진) 09:00-18:00"))
        assertNull(RemoteWorkSubject.parse(null))
    }

    @Test
    fun rejectsOutOfRangeTime() {
        assertNull(RemoteWorkSubject.parse("재택(현진) 25:00-18:00"))
        assertNull(RemoteWorkSubject.parse("재택(현진) 09:00-18:60"))
    }

    @Test
    fun withTimesKeepsOriginalStyle() {
        assertEquals("재택(양현진) 09:00 ~ 18:00", RemoteWorkSubject.withTimes("재택(양현진) 08:30 ~ 17:30", "09:00", "18:00"))
        assertEquals("재택(김다혜) 08:00-17:00 (오후 외근)", RemoteWorkSubject.withTimes("재택(김다혜) 7:30-16:30 (오후 외근)", "08:00", "17:00"))
        assertEquals("주간회의", RemoteWorkSubject.withTimes("주간회의", "09:00", "18:00"))
    }

    @Test
    fun sameNameIgnoresWhitespaceOnly() {
        kotlin.test.assertTrue(RemoteWorkSubject.sameName("양 현진", "양현진"))
        kotlin.test.assertFalse(RemoteWorkSubject.sameName("김현진", "양현진"))
        kotlin.test.assertFalse(RemoteWorkSubject.sameName("현진", "양현진"))
    }
}
