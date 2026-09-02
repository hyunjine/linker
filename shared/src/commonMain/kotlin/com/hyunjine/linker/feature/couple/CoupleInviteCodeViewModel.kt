package com.hyunjine.linker.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CoupleInviteCodeScreen — 진입 시 커플 생성/조회 후 상태를 결정한다.
 *
 * - [InviteCodeUiState.Loading]: 서버 응답 대기 · 초기 진입.
 * - [InviteCodeUiState.Solo]: 아직 파트너 조인 안 된 커플. 초대코드 노출.
 * - [InviteCodeUiState.Paired]: 이미 파트너와 연결됨. 코드 대신 안내 UI.
 *
 * 파트너 조인 여부는 `couples.linked_at` non-null 로 판정 (join_couple_by_invite 가 채움).
 */
class CoupleInviteCodeViewModel : ViewModel() {

    private val _state = MutableStateFlow<InviteCodeUiState>(InviteCodeUiState.Loading)
    val state: StateFlow<InviteCodeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val my = CouplesRepository.createOrGetMyCouple()
                val full = CouplesRepository.getCoupleById(my.id)
                full ?: CouplesRepository.CoupleFull(my.id, my.inviteCode, linkedAt = null)
            }.onSuccess { full ->
                println("[Couple] couple id=${full.id} code=${full.inviteCode} linked=${full.linkedAt}")
                _state.value = if (full.linkedAt != null) {
                    InviteCodeUiState.Paired
                } else {
                    InviteCodeUiState.Solo(full.inviteCode)
                }
            }.onFailure {
                println("[Couple] createOrGetMyCouple 실패: $it")
            }
        }
    }
}

/** CoupleInviteCodeScreen UI 상태. */
sealed interface InviteCodeUiState {
    /** 로딩 중 (초기 진입). Screen 은 placeholder 로 표시. */
    data object Loading : InviteCodeUiState

    /** 파트너 미조인. 초대코드 노출 + 복사/공유 액션. */
    data class Solo(val code: String) : InviteCodeUiState

    /** 이미 파트너와 연결됨. 코드 대신 안내 UI. */
    data object Paired : InviteCodeUiState
}
