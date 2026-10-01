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
    fun formatRoundTrips() {
        val subject = RemoteWorkSubject.format("현진", "10:00", "19:00")
        assertEquals("재택(현진) 10:00-19:00", subject)
        assertEquals(RemoteWorkSubject.Parsed("현진", "10:00", "19:00"), RemoteWorkSubject.parse(subject))
    }
}
