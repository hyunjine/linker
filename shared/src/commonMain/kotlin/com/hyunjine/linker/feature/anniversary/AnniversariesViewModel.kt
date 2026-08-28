package com.hyunjine.linker.feature.anniversary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.AnniversariesRepository
import com.hyunjine.linker.data.remote.CoupleRealtimeSubscription
import com.hyunjine.linker.data.remote.subscribeCoupleRealtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** AnniversariesScreen 의 state · 도메인 로직. 목록 로드 · 추가 · 삭제. */
class AnniversariesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AnniversariesUiState())
    val uiState: StateFlow<AnniversariesUiState> = _uiState.asStateFlow()

    private val realtimeJob: Job = viewModelScope.subscribeCoupleRealtime(
        CoupleRealtimeSubscription(onAnniversariesChanged = { reload() }),
    )

    init { reload() }

    override fun onCleared() {
        super.onCleared()
        realtimeJob.cancel()
    }

    private fun reload() {
        viewModelScope.launch {
            runCatching { AnniversariesRepository.list() }
                .onSuccess { rows ->
                    _uiState.update { it.copy(items = rows.map { r -> r.toUi() }) }
                }
                .onFailure { println("[Anniv] list 실패: $it") }
        }
    }

    fun add(title: String, date: LocalDate, repeatYearly: Boolean) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { AnniversariesRepository.create(title, date, repeatYearly) }
                .onSuccess { println("[Anniv] 저장 성공: $it") }
                .onFailure { println("[Anniv] 저장 실패: $it") }
            _uiState.update { it.copy(busy = false) }
            reload()
        }
    }

    fun delete(id: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { AnniversariesRepository.delete(id) }
                .onSuccess { println("[Anniv] 삭제 성공: $id") }
                .onFailure { println("[Anniv] 삭제 실패: $it") }
            _uiState.update { it.copy(busy = false) }
            reload()
        }
    }
}

data class AnniversariesUiState(
    val items: List<AnniversaryUi> = emptyList(),
    val busy: Boolean = false,
)

private fun AnniversariesRepository.Row.toUi(): AnniversaryUi = AnniversaryUi(
    id = id,
    title = title,
    date = LocalDate.parse(date),
    repeatYearly = repeatYearly,
)
