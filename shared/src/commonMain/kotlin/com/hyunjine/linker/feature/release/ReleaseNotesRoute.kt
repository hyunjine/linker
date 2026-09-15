package com.hyunjine.linker.feature.release

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 릴리즈 노트 라우트. NavDisplay entry 에서 이걸 호출.
 * ViewModel 은 라우트 스코프 — 진입 시 자동 fetch, 이탈 시 파기.
 */
@Composable
fun ReleaseNotesRoute(onBack: () -> Unit) {
    val viewModel: ReleaseNotesViewModel = viewModel { ReleaseNotesViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    ReleaseNotesScreen(
        releases = ui.releases,
        loading = ui.loading,
        error = ui.error,
        onBack = onBack,
        onRetry = viewModel::load,
    )
}
