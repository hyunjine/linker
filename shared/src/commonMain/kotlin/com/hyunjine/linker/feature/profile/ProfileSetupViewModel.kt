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
 * 신규 유저 온보딩 프로필 셋업. 저장 완료 시 [onSaved] 콜백.
 *
 * 저장 규칙:
 *  - `pickedImage` 가 있으면 우선 avatars 버킷에 업로드 → 그 public URL 을 profile_image_url 로 저장.
 *  - 없으면 Kakao provider `avatar_url` (`defaultAvatarUrl`) 을 그대로 저장.
 */
class ProfileSetupViewModel : ViewModel() {

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun save(
        nickname: String,
        birthDate: LocalDate?,
        pickedImage: ImageBitmap?,
        defaultAvatarUrl: String?,
        calendarColor: String,
        onSaved: () -> Unit,
    ) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            runCatching {
                val finalUrl = pickedImage
                    ?.let { AvatarsRepository.uploadPng(it.encodeToPngBytes()) }
                    ?: defaultAvatarUrl
                UsersRepository.completeProfile(
                    nickname = nickname,
                    birthDate = birthDate,
                    profileImageUrl = finalUrl,
                    calendarColor = calendarColor,
                )
            }.onSuccess {
                println("[Profile] 저장 성공")
                _saving.value = false
                onSaved()
            }.onFailure {
                println("[Profile] 저장 실패: $it")
                _saving.value = false
            }
        }
    }
}
