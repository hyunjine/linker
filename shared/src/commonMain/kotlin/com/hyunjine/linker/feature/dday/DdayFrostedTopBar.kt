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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.theme.SurfaceGray

/**
 * 화면 배경색 (SurfaceGray) 을 세로 그라디언트로 덮는 top bar (#329).
 *
 * 배경 색:
 *  - 위쪽 = SurfaceGray 알파 1 (화면 배경색 그대로 · 상단 status bar 영역까지 동일 색)
 *  - 아래쪽 = SurfaceGray 알파 0 (완전 투명)
 *
 * 위쪽 (status bar · 앱바 상단) 은 화면 background 와 완전히 같아 이음매 없이 붙고, 아래로
 * 갈수록 투명해져 스크롤되는 컨텐츠가 앱바 하단부로 자연스럽게 흘러들어오는 iOS 느낌을 낸다.
 * (표준 Compose 는 backdrop blur 를 소급 적용할 수 없어 알파 그라디언트로 근사)
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
    val fadeBrush = Brush.verticalGradient(
        0.0f to SurfaceGray,
        1.0f to SurfaceGray.copy(alpha = 0f),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fadeBrush)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        AppTopBar(title = title, onBack = onBack)
    }
}
