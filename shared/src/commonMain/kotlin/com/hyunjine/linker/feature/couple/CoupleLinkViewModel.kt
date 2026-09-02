package com.hyunjine.linker.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CoupleLinkScreen — 진입 시 현재 파트너 조인 상태를 확인한다.
 *
 * - [CoupleLinkUiState.Loading]: 서버 응답 대기.
 * - [CoupleLinkUiState.NotPaired]: 파트너 미조인. 두 개 옵션 (내 초대코드 · 상대 코드 입력) 노출.
 * - [CoupleLinkUiState.Paired]: 이미 파트너와 연결됨. 옵션 카드 감추고 안내 UI.
 *
 * 파트너 조인 여부는 `couples.linked_at` non-null 로 판정.
 * 아예 커플 자체가 없는 유저 (미가입) 는 NotPaired 로 취급 — 옵션 진입 시 초대코드 화면이
 * `create_my_couple` 로 자동 생성.
 */
class CoupleLinkViewModel : ViewModel() {

    private val _state = MutableStateFlow<CoupleLinkUiState>(CoupleLinkUiState.Loading)
    val state: StateFlow<CoupleLinkUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val id = CouplesRepository.myCoupleIdOrNull() ?: return@runCatching null
                CouplesRepository.getCoupleById(id)
            }.onSuccess { full ->
                _state.value = if (full?.linkedAt != null) {
                    CoupleLinkUiState.Paired
                } else {
                    CoupleLinkUiState.NotPaired
                }
            }.onFailure {
                println("[Couple] link status 조회 실패: $it")
                _state.value = CoupleLinkUiState.NotPaired
            }
        }
    }
}

sealed interface CoupleLinkUiState {
    data object Loading : CoupleLinkUiState
    data object NotPaired : CoupleLinkUiState
    data object Paired : CoupleLinkUiState
}
