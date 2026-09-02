package com.hyunjine.linker.data.local

import com.hyunjine.linker.feature.main.DrawerDisplayState
import com.hyunjine.linker.platform.LocalStorage

/**
 * 드로워 표시 옵션 (일정 · 달력 정보 5개 토글) 을 로컬에 영구 저장.
 *
 * 서버 저장 (`user_preferences`) 방식은 화면 이탈 · 프로세스 재시작 사이 in-flight 취소로
 * 유실되는 케이스가 있었음 (#167). 로컬 저장은 sync 라 이런 유실이 없다. 다른 기기로 로그인
 * 시 옵션이 안 따라오는 건 감수 (원래 이슈 #151 의 요구사항도 "로컬 저장").
 */
object DrawerDisplayLocal {
    private const val K_MY = "drawer.showMyCalendar"
    private const val K_PARTNER = "drawer.showPartnerCalendar"
    private const val K_SHARED = "drawer.showSharedCalendar"
    private const val K_HOLIDAYS = "drawer.showHolidays"
    private const val K_SOLAR = "drawer.showSolarTerms"

    fun load(): DrawerDisplayState = DrawerDisplayState(
        showMyCalendar = LocalStorage.getBoolean(K_MY, default = true),
        showPartnerCalendar = LocalStorage.getBoolean(K_PARTNER, default = true),
        showSharedCalendar = LocalStorage.getBoolean(K_SHARED, default = true),
        showHolidays = LocalStorage.getBoolean(K_HOLIDAYS, default = true),
        showSolarTerms = LocalStorage.getBoolean(K_SOLAR, default = true),
    )

    fun save(state: DrawerDisplayState) {
        LocalStorage.putBoolean(K_MY, state.showMyCalendar)
        LocalStorage.putBoolean(K_PARTNER, state.showPartnerCalendar)
        LocalStorage.putBoolean(K_SHARED, state.showSharedCalendar)
        LocalStorage.putBoolean(K_HOLIDAYS, state.showHolidays)
        LocalStorage.putBoolean(K_SOLAR, state.showSolarTerms)
    }
}
