package com.hyunjine.linker.feature.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 더보기 라우트 (#385). 드로워 하단 "더보기" 탭에서 진입.
 *
 * @param onBack 뒤로가기.
 * @param onReleaseNotesClick 릴리즈 노트 화면으로.
 * @param onLogout 로그아웃.
 */
@Composable
fun MoreRoute(
    onBack: () -> Unit,
    onReleaseNotesClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val viewModel: MoreViewModel = viewModel { MoreViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    MoreScreen(ui = ui, onBack = onBack, onReleaseNotesClick = onReleaseNotesClick, onLogout = onLogout)
}
