package com.hyunjine.linker.feature.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 알림 내역 라우트 (#303). 메인 상단바 종 아이콘에서 진입. VM 은 라우트 스코프 — 진입마다 fetch.
 *
 * @param onBack 뒤로가기.
 */
@Composable
fun NotificationsRoute(onBack: () -> Unit) {
    val viewModel: NotificationsViewModel = viewModel { NotificationsViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsScreen(ui = ui, onBack = onBack, onRetry = viewModel::load, onRefresh = viewModel::refresh)
}
