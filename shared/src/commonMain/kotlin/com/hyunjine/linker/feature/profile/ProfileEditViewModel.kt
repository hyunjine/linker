package com.hyunjine.linker.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.UsersRepository
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

    init {
        viewModelScope.launch {
            val profile = runCatching { UsersRepository.myProfile() }
                .onFailure { println("[ProfileEdit] 프로필 로드 실패: $it") }
                .getOrNull()
            _uiState.value = _uiState.value.copy(profile = profile, loaded = true)
        }
    }

    fun save(
        nickname: String,
        birthDate: LocalDate?,
        calendarColor: String,
        onSaved: () -> Unit,
    ) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            runCatching {
                UsersRepository.updateProfile(
                    nickname = nickname,
                    birthDate = birthDate,
                    calendarColor = calendarColor,
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
