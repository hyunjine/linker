package com.hyunjine.linker.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.data.remote.SchedulesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * CreateScheduleScreen — 신규 · 편집 겸용. 진입 시 [scheduleId]/[initialDate]/[initialType] 로부터
 * seed draft 계산. 편집이면 DB 에서 draft prefetch. 저장/수정/삭제 시 [onDone] 콜백.
 */
class CreateScheduleViewModel(
    private val scheduleId: String?,
    initialDate: String?,
    initialType: String?,
) : ViewModel() {

    val editing: Boolean = scheduleId != null

    private val _uiState = MutableStateFlow(CreateScheduleUiState(loaded = !editing))
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (scheduleId == null) {
                val seededDate = initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val seededType = when (initialType) {
                    "task" -> ScheduleType.Task
                    "schedule" -> ScheduleType.Schedule
                    else -> null
                }
                _uiState.value = _uiState.value.copy(
                    initial = if (seededDate != null || seededType != null) {
                        val date = seededDate ?: todayLocalDate()
                        ScheduleDraft(
                            startDate = date,
                            endDate = date,
                            type = seededType ?: ScheduleType.Schedule,
                        )
                    } else null,
                    loaded = true,
                )
                return@launch
            }
            runCatching { SchedulesRepository.getDraftById(scheduleId) }
                .onSuccess { _uiState.value = _uiState.value.copy(initial = it, loaded = true) }
                .onFailure {
                    println("[Schedule] getDraftById 실패: $it")
                    _uiState.value = _uiState.value.copy(loaded = true)
                }
        }
    }

    fun save(draft: ScheduleDraft, onDone: () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            val op = if (scheduleId != null) {
                runCatching { SchedulesRepository.update(scheduleId, draft) }
            } else {
                runCatching { SchedulesRepository.create(draft) }
            }
            op.onSuccess {
                println("[Schedule] ${if (editing) "수정" else "저장"} 성공")
                _uiState.value = _uiState.value.copy(saving = false)
                onDone()
            }.onFailure {
                println("[Schedule] ${if (editing) "수정" else "저장"} 실패: $it")
                _uiState.value = _uiState.value.copy(saving = false)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = scheduleId ?: return
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            runCatching { SchedulesRepository.delete(id) }
                .onSuccess {
                    println("[Schedule] 삭제 성공: $id")
                    _uiState.value = _uiState.value.copy(saving = false)
                    onDone()
                }
                .onFailure {
                    println("[Schedule] 삭제 실패: $it")
                    _uiState.value = _uiState.value.copy(saving = false)
                }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun todayLocalDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

data class CreateScheduleUiState(
    val loaded: Boolean = false,
    val initial: ScheduleDraft? = null,
    val saving: Boolean = false,
)
