package com.hyunjine.linker.feature.main

import androidx.compose.ui.graphics.Color
import com.hyunjine.linker.designsystem.theme.CalendarPurple
import com.hyunjine.linker.designsystem.theme.calendarColorFor

/**
 * 스케줄 chip · row 등에 쓰이는 소유자별 색상. 내 프로필 · 파트너 프로필의 `calendar_color` 로부터
 * 파생. 프로필 로드 전에는 [Default] fallback 팔레트 사용.
 */
data class OwnerColors(val me: Color, val partner: Color, val us: Color) {
    fun forOwner(ownerKind: String): Color = when (ownerKind) {
        "me" -> me
        "partner" -> partner
        else -> us
    }

    companion object {
        val Default = OwnerColors(
            me = calendarColorFor("blue"),
            partner = calendarColorFor("pink"),
            us = CalendarPurple,
        )
    }
}
