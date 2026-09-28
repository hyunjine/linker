package com.hyunjine.linker.feature.task

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 할 일 내역 라우트 (#304). NavDisplay entry 에서 호출. VM 은 라우트 스코프 — 진입 시 fetch.
 *
 * @param onBack 뒤로가기.
 * @param onEditTask 행 탭 → 편집 화면 진입 (schedules.id 전달).
 * @param onTasksChanged 체크 토글이 서버에 반영됐을 때 — 캘린더 재fetch 트리거용.
 * @param scheduleRefreshTick 편집 화면 저장 등으로 스케줄이 바뀌면 증가. 바뀔 때마다 조용히 재조회.
 */
@Composable
fun TasksRoute(
    onBack: () -> Unit,
    onEditTask: (String) -> Unit,
    onTasksChanged: () -> Unit,
    scheduleRefreshTick: Int,
) {
    val viewModel: TasksViewModel = viewModel { TasksViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    // 진입 시점 tick 은 init 로드가 이미 커버 → 이후 변경분만 재조회.
    val initialTick = remember { scheduleRefreshTick }
    LaunchedEffect(scheduleRefreshTick) {
        if (scheduleRefreshTick != initialTick) viewModel.load(silent = true)
    }

    TasksScreen(
        ui = ui,
        onBack = onBack,
        onSelectTab = viewModel::selectTab,
        onSelectSort = viewModel::selectSort,
        onToggleDone = { id -> viewModel.toggleDone(id, onTasksChanged) },
        onTaskClick = onEditTask,
        onRetry = { viewModel.load() },
    )
}
