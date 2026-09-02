package com.hyunjine.linker.data.remote

import com.hyunjine.linker.feature.main.DrawerDisplayState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `public.user_preferences` — 유저별 드로워 표시 옵션 저장소. RLS 로 본인 row 만 접근.
 *
 * 서버-사이드 저장을 택한 이유:
 *  - 스키마가 이미 5개 컬럼 (show_my_calendar 등) 으로 정의돼 있어 새 DB 작업 불필요
 *  - 새 로컬 저장 의존성 (multiplatform-settings 등) 추가 필요 없음
 *  - 같은 유저가 다른 기기로 로그인해도 옵션이 따라옴 (부가 가치)
 *
 * `show_lunar` 는 스키마에 있지만 현재 UI 미노출 — 이 리포지토리는 다루지 않고 서버 default(false) 유지.
 */
object UserPreferencesRepository {

    @Serializable
    data class Row(
        @SerialName("user_id") val userId: String,
        @SerialName("show_my_calendar") val showMyCalendar: Boolean = true,
        @SerialName("show_partner_calendar") val showPartnerCalendar: Boolean = true,
        @SerialName("show_shared_calendar") val showSharedCalendar: Boolean = true,
        @SerialName("show_holidays") val showHolidays: Boolean = true,
        @SerialName("show_solar_terms") val showSolarTerms: Boolean = true,
    )

    /**
     * 내 표시 옵션 조회. row 가 없으면 (신규 유저) [DrawerDisplayState] 기본값 반환.
     * 세션이 없으면 (미로그인) null.
     */
    suspend fun myDisplay(): DrawerDisplayState? {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return null
        val row = SupabaseProvider.client.from("user_preferences")
            .select { filter { eq("user_id", uid) } }
            .decodeSingleOrNull<Row>()
        return row?.toDisplay() ?: DrawerDisplayState()
    }

    /**
     * 내 표시 옵션 upsert. PK 가 user_id 라 idempotent.
     * `onConflict = "user_id"` 를 명시해 supabase-kt 가 항상 merge-duplicates 로 동작하도록 강제.
     * 실패는 상위 (VM) 에서 로그만 남기고 옵티미스틱 UI 유지.
     */
    suspend fun upsertMyDisplay(display: DrawerDisplayState) {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("세션 없이 표시 옵션 저장 시도")
        SupabaseProvider.client.from("user_preferences")
            .upsert(display.toRow(uid)) { onConflict = "user_id" }
    }
}

private fun UserPreferencesRepository.Row.toDisplay() = DrawerDisplayState(
    showMyCalendar = showMyCalendar,
    showPartnerCalendar = showPartnerCalendar,
    showSharedCalendar = showSharedCalendar,
    showHolidays = showHolidays,
    showSolarTerms = showSolarTerms,
)

private fun DrawerDisplayState.toRow(uid: String) = UserPreferencesRepository.Row(
    userId = uid,
    showMyCalendar = showMyCalendar,
    showPartnerCalendar = showPartnerCalendar,
    showSharedCalendar = showSharedCalendar,
    showHolidays = showHolidays,
    showSolarTerms = showSolarTerms,
)
