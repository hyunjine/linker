package com.hyunjine.linker.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** CoupleJoinScreen — 상대방 초대코드 입력 후 커플 join. 성공 시 [onJoined] 콜백. */
class CoupleJoinViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CoupleJoinUiState())
    val uiState: StateFlow<CoupleJoinUiState> = _uiState.asStateFlow()

    fun link(partnerCode: String, onJoined: () -> Unit) {
        if (_uiState.value.linking) return
        _uiState.update { it.copy(linking = true) }
        viewModelScope.launch {
            runCatching { CouplesRepository.joinByInviteCode(partnerCode) }
                .onSuccess {
                    println("[Couple] joined couple $it → 홈으로 이동")
                    _uiState.update { it.copy(linking = false) }
                    onJoined()
                }
                .onFailure {
                    println("[Couple] join 실패: $it")
                    _uiState.update { it.copy(linking = false) }
                }
        }
    }
}

data class CoupleJoinUiState(val linking: Boolean = false)
