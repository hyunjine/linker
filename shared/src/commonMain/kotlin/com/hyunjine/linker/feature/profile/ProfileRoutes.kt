package com.hyunjine.linker.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 온보딩 프로필 셋업 라우트. [currentUser] 는 카카오 provider 가 채운 metadata 를 초기값으로 씀.
 * 저장 완료 시 [onSaved] 콜백.
 */
@Composable
fun ProfileSetupRoute(
    currentUser: UserInfo?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: ProfileSetupViewModel = viewModel { ProfileSetupViewModel() }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val defaults = remember(currentUser) { profileDefaults(currentUser) }
    ProfileSetupScreen(
        nickname = defaults.nickname,
        defaultAvatarUrl = defaults.avatarUrl.toSecureImageUrl(),
        saving = saving,
        onBack = onBack,
        onNext = { nickname, birthDate, colorId ->
            viewModel.save(
                nickname = nickname,
                birthDate = birthDate,
                avatarUrl = defaults.avatarUrl.toSecureImageUrl(),
                calendarColor = colorId,
                onSaved = onSaved,
            )
        },
    )
}

/**
 * 프로필 편집 라우트. 프리필 로드 완료 뒤에만 mount (rememberSaveable 초기화 이슈 회피).
 * 저장 완료 시 [onSaved] 콜백.
 */
@Composable
fun ProfileEditRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: ProfileEditViewModel = viewModel { ProfileEditViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    if (!ui.loaded) return
    val p = ui.profile ?: run {
        println("[ProfileEdit] 프로필 없음 — 편집 화면 진입 취소")
        onBack()
        return
    }
    ProfileSetupScreen(
        nickname = p.nickname ?: "",
        birthDate = p.birthDate?.let(::isoToDisplayBirthDate) ?: "2000. 01. 01.",
        selectedColorId = p.calendarColor,
        defaultAvatarUrl = p.profileImageUrl.toSecureImageUrl(),
        submitText = "저장",
        saving = ui.saving,
        onBack = onBack,
        onNext = { nickname, birthDate, colorId ->
            viewModel.save(nickname, birthDate, colorId, onSaved)
        },
    )
}

// ────────── helpers (App.kt 에서 이관) ──────────

/** Kakao provider metadata 에서 프로필 셋업 초기값 추출. */
internal data class ProfileDefaults(val nickname: String, val avatarUrl: String?)

internal fun profileDefaults(user: UserInfo?): ProfileDefaults {
    val meta: JsonObject? = user?.userMetadata
    // 카카오 provider 가 채운 값만 사용. 없으면 빈 문자열/null — 사용자가 직접 입력 · 선택.
    val nickname = meta?.get("full_name")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
        ?: ""
    val avatar = meta?.get("avatar_url")?.jsonPrimitive?.contentOrNull
    return ProfileDefaults(nickname = nickname, avatarUrl = avatar)
}

/** Kakao CDN http URL → https 강제 (Android 9+ · iOS ATS cleartext 차단 우회). */
internal fun String?.toSecureImageUrl(): String? =
    this?.replace(Regex("^http://"), "https://")

/** ISO date (yyyy-MM-dd) → ProfileSetupScreen 이 파싱하는 "yyyy. MM. dd." 포맷. */
internal fun isoToDisplayBirthDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return "2000. 01. 01."
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}. $m. $d."
}
