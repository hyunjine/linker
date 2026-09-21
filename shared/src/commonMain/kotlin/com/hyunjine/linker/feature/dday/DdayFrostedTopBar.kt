package com.hyunjine.linker.feature.dday

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
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.theme.SurfaceGray

/**
 * 화면 배경색과 동일한 SurfaceGray 단색 top bar (#329).
 *
 * 이전엔 하단 fade 그라디언트로 iOS backdrop blur 를 근사했으나, 그라디언트 하단부에서 컨텐츠가
 * 살짝 비쳐 보이는 톤이 오히려 산만해 단색으로 정리. status bar 부터 앱바 하단까지 화면 배경과
 * 완전히 동일한 색으로 덮여 스크롤 시 이질감 없이 컨텐츠와 분리.
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
