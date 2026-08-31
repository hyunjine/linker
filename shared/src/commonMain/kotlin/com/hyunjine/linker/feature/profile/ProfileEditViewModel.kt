package com.hyunjine.linker.feature.profile

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.AvatarsRepository
import com.hyunjine.linker.data.remote.UsersRepository
import com.hyunjine.linker.platform.encodeToPngBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 프로필 편집. 진입 시 현재 값 prefetch → uiState 로 노출. 저장 시 updateProfile 호출.
 * 성공 시 [onSaved] 콜백으로 상위 (App) 가 refresh tick bump · back navigate.
 */
class ProfileEditViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    init { reload() }

    /**
     * DB 에서 프로필 다시 조회. Route composable 이 재진입할 때 호출해 저장 직후 값이 즉시 반영되게 한다
     * (VM 이 nav 재사용으로 살아있는 경우 init 이 한 번만 돌아 stale 상태로 남는 문제 해결).
     */
    fun reload() {
        viewModelScope.launch {
            val profile = runCatching { UsersRepository.myProfile() }
                .onFailure { println("[ProfileEdit] 프로필 로드 실패: $it") }
                .getOrNull()
            _uiState.value = _uiState.value.copy(profile = profile, loaded = true)
        }
    }

    /**
     * @param pickedImage 사용자가 photo picker 로 새로 고른 이미지. null 이면 사진 변경 안 함.
     *                    있으면 avatars 버킷에 PNG 로 업로드하고 새 URL 을 profile_image_url 에 함께 저장.
     */
    fun save(
        nickname: String,
        birthDate: LocalDate?,
        calendarColor: String,
        pickedImage: ImageBitmap?,
        onSaved: () -> Unit,
    ) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            runCatching {
                // 사진이 새로 선택됐으면 업로드 먼저, 그 URL 을 updateProfile 로 함께 저장.
                val newUrl = pickedImage?.let { AvatarsRepository.uploadPng(it.encodeToPngBytes()) }
                UsersRepository.updateProfile(
                    nickname = nickname,
                    birthDate = birthDate,
                    calendarColor = calendarColor,
                    profileImageUrl = newUrl,
                )
            }.onSuccess {
                println("[ProfileEdit] 저장 성공")
                _uiState.value = _uiState.value.copy(saving = false)
                onSaved()
            }.onFailure {
                println("[ProfileEdit] 저장 실패: $it")
                _uiState.value = _uiState.value.copy(saving = false)
            }
        }
    }
}

data class ProfileEditUiState(
    val loaded: Boolean = false,
    val profile: UsersRepository.Profile? = null,
    val saving: Boolean = false,
)
