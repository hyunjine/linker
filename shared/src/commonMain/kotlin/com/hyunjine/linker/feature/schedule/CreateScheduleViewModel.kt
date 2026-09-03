package com.hyunjine.linker.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.SchedulesRepository
import com.hyunjine.linker.platform.refreshTodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CreateScheduleScreen — 신규 · 편집 겸용.
 * - 신규(create): initial 은 Route 가 인자로 즉시 만들어 Screen 에 넘김. VM 은 저장 · 삭제만 담당.
 * - 편집(edit): [scheduleId] 로 DB 에서 prefetch → uiState.initial 에 담아 Screen 에 노출.
 *
 * VM 이 신규 case 의 initial 을 async 로 계산하던 이전 구조는 Route 가 initial=null 상태로 잠깐
 * mount → Screen 의 rememberSaveable 이 default 로 굳어져 Task pill 이 안 먹히던 #143 원인이었음.
 */
class CreateScheduleViewModel(
    private val scheduleId: String?,
) : ViewModel() {

    val editing: Boolean = scheduleId != null

    // edit 모드만 async fetch 대기. create 는 uiState 를 안 쓴다.
    private val _uiState = MutableStateFlow(CreateScheduleUiState(loaded = !editing))
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    init { reloadFromDb() }

    /**
     * 편집 모드일 때 DB 에서 draft 를 다시 조회. Route 재진입마다 호출해서 저장 직후 재편집해도
     * 최신 값이 반영되게 한다. Nav 재진입이 같은 scheduleId key 로 VM 을 재사용해 init 이 한 번만
     * 도는 경우를 방어 (ProfileEdit 와 동일 패턴).
     */
    fun reloadFromDb() {
        if (!editing || scheduleId == null) return
        viewModelScope.launch {
            runCatching { SchedulesRepository.getDraftById(scheduleId) }
                .onSuccess { _uiState.value = _uiState.value.copy(initial = it, loaded = true) }
                .onFailure {
                    println("[Schedule] getDraftById 실패: $it")
                    _uiState.value = _uiState.value.copy(loaded = true)
                }
        }
    }

    /**
     * 신규 · 편집 겸용 저장.
     *
     * [scope] 는 시리즈 인스턴스 편집일 때만 의미. 신규 저장 · 단일 스케줄 편집이면 null 로 오고,
     * 반복 시리즈 인스턴스를 편집할 때만 UI 다이얼로그가 [SeriesEditScope] 를 채워 넘긴다.
     */
    fun save(draft: ScheduleDraft, scope: SeriesEditScope? = null, onDone: () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            val op = when {
                scheduleId == null -> runCatching { SchedulesRepository.create(draft) }
                scope == SeriesEditScope.OnlyThis ->
                    runCatching { SchedulesRepository.updateOnlyThis(scheduleId, draft); scheduleId }
                scope == SeriesEditScope.ThisAndFuture ->
                    runCatching { SchedulesRepository.updateThisAndFuture(scheduleId, draft) }
                else -> runCatching { SchedulesRepository.update(scheduleId, draft) }
            }
            op.onSuccess {
                println("[Schedule] ${if (editing) "수정" else "저장"} 성공 (scope=$scope)")
                _uiState.value = _uiState.value.copy(saving = false)
                refreshTodayWidget()
                onDone()
            }.onFailure {
                println("[Schedule] ${if (editing) "수정" else "저장"} 실패: $it")
                _uiState.value = _uiState.value.copy(saving = false)
            }
        }
    }

    /**
     * 삭제.
     *
     * [scope] 는 시리즈 인스턴스일 때만 의미:
     *  - null · [SeriesEditScope.OnlyThis]: 이 row 만 삭제 (시리즈에 속해도 형제들은 유지)
     *  - [SeriesEditScope.ThisAndFuture]: 이 row 이후 (start_date >= 현재) 시리즈 rows 를 batch 삭제
     */
    fun delete(scope: SeriesEditScope? = null, onDone: () -> Unit) {
        val id = scheduleId ?: return
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            val op = when (scope) {
                SeriesEditScope.ThisAndFuture ->
                    runCatching { SchedulesRepository.deleteThisAndFuture(id) }
                else ->
                    runCatching { SchedulesRepository.deleteOnlyThis(id) }
            }
            op.onSuccess {
                println("[Schedule] 삭제 성공: $id (scope=$scope)")
                _uiState.value = _uiState.value.copy(saving = false)
                refreshTodayWidget()
                onDone()
            }.onFailure {
                println("[Schedule] 삭제 실패: $it")
                _uiState.value = _uiState.value.copy(saving = false)
            }
        }
    }
}

data class CreateScheduleUiState(
    val loaded: Boolean = false,
    val initial: ScheduleDraft? = null,
    val saving: Boolean = false,
)
