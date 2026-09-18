package com.hyunjine.linker.feature.everytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.EverytimeException
import com.hyunjine.linker.data.remote.EverytimeRepository
import com.hyunjine.linker.data.remote.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 파트너의 에브리타임 시간표를 조회 · 렌더 (#306).
 *
 * 진입 시퀀스:
 *  1. UsersRepository.partnerProfile() 로 상대방의 identifier 조회.
 *  2. 없으면 [EverytimeUiState.error] 로 안내 (드로워 게이트가 이미 걸러줬어야 하는 상태이므로 에러 취급).
 *  3. 있으면 Repository.fetchTimetable(identifier) 호출 → 대표 학기 로드.
 *  4. 사용자가 학기 스위처에서 다른 학기를 고르면 [selectSemester] 로 재 fetch.
 *
 * 학기 목록은 첫 응답의 `availableSemesters` 를 그대로 사용. 학기 이동은 매번 API 호출 —
 * Everytime 은 학기별로 다른 identifier 를 발급하므로 캐시하지 않고 요청마다 새로 받는다.
 */
class EverytimeTimetableViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EverytimeUiState())
    val uiState: StateFlow<EverytimeUiState> = _uiState.asStateFlow()

    init { loadInitial() }

    private fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val partner = runCatching { UsersRepository.partnerProfile() }
                .onFailure { println("[Everytime] 파트너 프로필 조회 실패: $it") }
                .getOrNull()
            val identifier = partner?.everytimeIdentifier
            if (identifier.isNullOrBlank()) {
                _uiState.value = EverytimeUiState(
                    loading = false,
                    error = "파트너가 에브리타임 URL 을 등록하지 않았어요.",
                )
                return@launch
            }
            fetch(identifier)
        }
    }

    fun selectSemester(ref: SemesterRef) {
        val current = _uiState.value.timetable?.identifier
        if (current == ref.identifier) return
        fetch(ref.identifier)
    }

    fun retry() {
        val currentId = _uiState.value.timetable?.identifier
        if (currentId != null) fetch(currentId) else loadInitial()
    }

    private fun fetch(identifier: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { EverytimeRepository.fetchTimetable(identifier) }
                .onSuccess { data ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        timetable = data,
                        // 첫 응답의 primaryTables 를 학기 스위처의 truth source 로 잡는다. 이후 학기 이동
                        // 응답도 primaryTables 를 그대로 다시 담아 오지만, 순서/포함 관계가 안정적이라
                        // 매번 대체해도 무방.
                        semesters = data.availableSemesters.takeIf { it.isNotEmpty() }
                            ?: _uiState.value.semesters,
                    )
                }
                .onFailure { e ->
                    println("[Everytime] fetch 실패: $e")
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = (e as? EverytimeException)?.message ?: "시간표를 불러오지 못했어요.",
                    )
                }
        }
    }
}

data class EverytimeUiState(
    val loading: Boolean = true,
    val timetable: EverytimeTimetable? = null,
    val semesters: List<SemesterRef> = emptyList(),
    val error: String? = null,
)
