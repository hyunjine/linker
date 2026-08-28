package com.hyunjine.linker.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 신규 유저 온보딩 프로필 셋업. 저장 완료 시 [onSaved] 콜백 (App 이 CoupleLink 로 이동).
 */
class ProfileSetupViewModel : ViewModel() {

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun save(
        nickname: String,
        birthDate: LocalDate?,
        avatarUrl: String?,
        calendarColor: String,
        onSaved: () -> Unit,
    ) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            runCatching {
                UsersRepository.completeProfile(
                    nickname = nickname,
                    birthDate = birthDate,
                    profileImageUrl = avatarUrl,
                    calendarColor = calendarColor,
                )
            }.onSuccess {
                println("[Profile] 저장 성공 → CoupleLink 로 이동")
                _saving.value = false
                onSaved()
            }.onFailure {
                println("[Profile] 저장 실패: $it")
                _saving.value = false
            }
        }
    }
}
