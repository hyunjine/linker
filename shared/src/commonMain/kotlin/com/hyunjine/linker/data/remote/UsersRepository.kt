package com.hyunjine.linker.data.remote

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * `public.users` 프로필 CRUD. Supabase Postgrest 로 위임.
 * RLS 정책 `users_update_self` 로 `auth.uid()` 와 일치하는 row 만 갱신 가능하므로,
 * 별도 권한 체크 없이 항상 "내 것" 만 조작한다.
 */
object UsersRepository {

    /**
     * `public.users` row. `handle_new_auth_user` 트리거로 로그인 직후 껍데기가 자동 생성되므로
     * 프로필 셋업 이전 유저도 이 row 는 존재한다 — 다만 nickname/birth_date 등은 NULL.
     * [profileCompletedAt] 이 NULL 이면 아직 온보딩 미완료.
     */
    @Serializable
    data class Profile(
        val id: String,
        val nickname: String? = null,
        @SerialName("birth_date") val birthDate: String? = null,
        @SerialName("profile_image_url") val profileImageUrl: String? = null,
        @SerialName("calendar_color") val calendarColor: String = "blue",
        @SerialName("profile_completed_at") val profileCompletedAt: String? = null,
    ) {
        val isCompleted: Boolean get() = profileCompletedAt != null
    }

    /** 현재 유저의 `public.users` row. 세션 없거나 트리거가 아직 안 돌았으면 null. */
    suspend fun myProfile(): Profile? {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return null
        return SupabaseProvider.client.from("users")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Profile>()
    }

    /**
     * 온보딩 프로필 셋업 완료 저장. `profile_completed_at` 을 now() 로 세팅해
     * 이후 라우팅에서 이 화면을 스킵할 수 있는 신호로 사용한다.
     *
     * @param nickname 표시 닉네임 (필수).
     * @param birthDate 생년월일. null 이면 컬럼도 NULL 로 남긴다.
     * @param profileImageUrl 프로필 사진 URL. Kakao provider avatar_url 기본값이 들어오는 경로.
     * @param calendarColor `Color.kt` 의 캘린더 색상 id (`blue`, `pink`, ...).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun completeProfile(
        nickname: String,
        birthDate: LocalDate?,
        profileImageUrl: String?,
        calendarColor: String,
    ) {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("로그인되지 않은 상태에서 프로필 저장 시도")
        SupabaseProvider.client.from("users").update({
            set("nickname", nickname)
            set("birth_date", birthDate?.toString())
            set("profile_image_url", profileImageUrl)
            set("calendar_color", calendarColor)
            set("profile_completed_at", Clock.System.now().toString())
        }) {
            filter { eq("id", uid) }
        }
    }
}
