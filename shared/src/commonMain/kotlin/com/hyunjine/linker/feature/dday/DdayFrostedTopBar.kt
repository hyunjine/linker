package com.hyunjine.linker.feature.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hyunjine.linker.designsystem.common.AppTopBar

/**
 * iOS 스타일 반투명 top bar (#329). 스크롤 시 뒤 컨텐츠와 겹치는 문제 해결용.
 *
 * 표준 Compose 만으로 실제 backdrop blur 를 소급 적용할 수 없어 (LiquidGlass.kt 도 동일 이유로
 * 반투명 fill 로 근사) 여기서도 흰색 알파 0.85 로 iOS Wi-Fi 설정 화면 톤을 근사한다.
 * status bar 영역까지 함께 덮도록 windowInsetsPadding 을 outer 아닌 inner AppTopBar 근처에
 * 배치한다.
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
            .background(Color.White.copy(alpha = 0.85f))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        AppTopBar(title = title, onBack = onBack)
    }
}
