package com.hyunjine.linker.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsCalendarColorResolveTest {

    @Test
    fun couple_value_wins_over_my_value() {
        assertEquals("mint", resolveUsCalendarColorId(coupleColor = "mint", myColor = "yellow"))
        assertEquals("#FF3282", resolveUsCalendarColorId(coupleColor = "#FF3282", myColor = null))
    }

    @Test
    fun falls_back_to_my_value_when_couple_value_missing() {
        assertEquals("yellow", resolveUsCalendarColorId(coupleColor = null, myColor = "yellow"))
    }

    @Test
    fun null_when_both_missing() {
        // 호출부가 CalendarPurple 로 fallback.
        assertNull(resolveUsCalendarColorId(coupleColor = null, myColor = null))
    }
}
