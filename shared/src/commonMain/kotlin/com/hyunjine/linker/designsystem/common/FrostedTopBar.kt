package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hyunjine.linker.designsystem.theme.SurfaceGray

/**
 * 화면 배경색과 동일한 SurfaceGray 단색 top bar (#329).
 *
 * status bar 부터 앱바 하단까지 SurfaceGray 로 덮어 스크롤 시 컨텐츠와 이질감 없이 분리.
 * D-day · 에브리타임 등 스크롤이 가능한 화면에서 공용으로 씀.
 *
 * @param title 앱바 중앙 타이틀.
 * @param onBack 좌측 뒤로가기 콜백.
 */
@Composable
fun FrostedTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceGray)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        AppTopBar(title = title, onBack = onBack)
    }
}
