package com.hyunjine.linker.api.users

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * `docs/api-design.md` §4 Users. `is_new` 는 로그인 응답에서만 세팅되므로 별도.
 */

/** 캘린더 색상 옵션. `ProfileSetupScreen` 의 8종과 일치. */
enum class CalendarColor { blue, mint, green, yellow, orange, pink, purple, gray }

@Serializable
data class UserResponse(
    val id: String,
    val kakaoId: Long,
    val nickname: String? = null,
    val birthDate: LocalDate? = null,
    val profileImageUrl: String? = null,
    val calendarColor: CalendarColor = CalendarColor.blue,
    val isProfileComplete: Boolean = false,
    val profileCompletedAt: Instant? = null,
    /** `/auth/kakao` 응답에서만 true 가능. `GET /users/me` 응답에는 항상 null. */
    val isNew: Boolean? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** `POST /users/me/profile` — 최초 프로필 완성 (회원가입 마무리). */
@Serializable
data class ProfileSetupRequest(
    val nickname: String,
    val birthDate: LocalDate,
    val calendarColor: CalendarColor,
    /** 업로드 결과 URL. null 허용 여부는 서버 설정. */
    val profileImageUrl: String? = null,
)

/** `PATCH /users/me` — 완성 이후 부분 수정. */
@Serializable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val birthDate: LocalDate? = null,
    val calendarColor: CalendarColor? = null,
    val profileImageUrl: String? = null,
)

/** `/users/me/preferences` — 캘린더 표시 옵션. */
@Serializable
data class UserPreferencesDto(
    val showMyCalendar: Boolean = true,
    val showPartnerCalendar: Boolean = true,
    val showHolidays: Boolean = true,
    val showSolarTerms: Boolean = true,
    val showLunar: Boolean = false,
    val updatedAt: Instant? = null,
)

@Serializable
data class UpdateUserPreferencesRequest(
    val showMyCalendar: Boolean? = null,
    val showPartnerCalendar: Boolean? = null,
    val showHolidays: Boolean? = null,
    val showSolarTerms: Boolean? = null,
    val showLunar: Boolean? = null,
)
