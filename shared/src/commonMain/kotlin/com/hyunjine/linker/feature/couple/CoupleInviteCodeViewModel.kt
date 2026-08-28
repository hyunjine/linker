package com.hyunjine.linker.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.CouplesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** CoupleInviteCodeScreen — 진입 시 커플 생성/조회하고 초대코드 노출. */
class CoupleInviteCodeViewModel : ViewModel() {

    private val _myCode = MutableStateFlow<String?>(null)
    val myCode: StateFlow<String?> = _myCode.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { CouplesRepository.createOrGetMyCouple() }
                .onSuccess {
                    println("[Couple] my couple id=${it.id} code=${it.inviteCode}")
                    _myCode.value = it.inviteCode
                }
                .onFailure { println("[Couple] createOrGetMyCouple 실패: $it") }
        }
    }
}
