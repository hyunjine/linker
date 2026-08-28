package com.hyunjine.linker.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 스케줄 생성 · 편집 라우트. [scheduleId] 있으면 편집. [initialDate] · [initialType] 은 신규 진입 seed.
 * 저장/삭제 완료 시 [onDone] 콜백 (App 이 pop).
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
        CreateScheduleViewModel(scheduleId, initialDate, initialType)
    }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    if (!ui.loaded) return
    CreateScheduleScreen(
        initial = ui.initial,
        editing = viewModel.editing,
        onBack = onBack,
        onSave = { draft -> viewModel.save(draft, onDone) },
        onDelete = { viewModel.delete(onDone) },
    )
}
