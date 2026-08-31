package com.hyunjine.linker.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 스케줄 생성 · 편집 라우트. [scheduleId] 있으면 편집. [initialDate] · [initialType] 은 신규 진입 seed.
 * 저장/삭제 완료 시 [onDone] 콜백 (App 이 pop).
 *
 * 신규 case: initial 을 Route 에서 sync 로 즉시 만들어 Screen 에 넘김 (VM 우회) — 이렇게 해야
 * Screen 이 mount 되는 순간 initial 이 이미 확정돼 rememberSaveable 이 첫 값을 정확히 잡는다.
 * 편집 case: VM 이 DB 에서 prefetch → uiState.loaded=true 후 mount.
 */
@Composable
fun CreateScheduleRoute(
    scheduleId: String? = null,
    initialDate: String? = null,
    initialType: String? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: CreateScheduleViewModel = viewModel(key = scheduleId ?: "new") {
        CreateScheduleViewModel(scheduleId)
    }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    if (viewModel.editing) {
        // DB fetch 완료 대기.
        if (!ui.loaded) return
        CreateScheduleScreen(
            initial = ui.initial,
            editing = true,
            onBack = onBack,
            onSave = { draft -> viewModel.save(draft, onDone) },
            onDelete = { viewModel.delete(onDone) },
        )
    } else {
        // 신규: initialType/initialDate 로 seed 를 여기서 즉시 만들어 넘김.
        val initial = remember(initialDate, initialType) {
            buildCreateInitial(initialDate, initialType)
        }
        CreateScheduleScreen(
            initial = initial,
            editing = false,
            onBack = onBack,
            onSave = { draft -> viewModel.save(draft, onDone) },
            onDelete = { viewModel.delete(onDone) },
        )
    }
}

/**
 * initialType/initialDate 로부터 신규 draft seed 생성. 둘 다 null 이면 null 반환 —
 * Screen 이 default (오늘 · Schedule) 로 초기화.
 */
@OptIn(ExperimentalTime::class)
private fun buildCreateInitial(initialDate: String?, initialType: String?): ScheduleDraft? {
    val seededDate = initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val seededType = when (initialType) {
        "task" -> ScheduleType.Task
        "schedule" -> ScheduleType.Schedule
        else -> null
    }
    if (seededDate == null && seededType == null) return null
    val date = seededDate ?: today()
    return ScheduleDraft(
        startDate = date,
        endDate = date,
        type = seededType ?: ScheduleType.Schedule,
    )
}

@OptIn(ExperimentalTime::class)
private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
