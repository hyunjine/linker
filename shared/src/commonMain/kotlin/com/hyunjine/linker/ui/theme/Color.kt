package com.hyunjine.linker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 앱 전역 팔레트. 새 색이 필요하면 여기서만 정의하고 screen 파일에서는 import 만 한다.

// 기본 배경/텍스트
val Background = Color.White
val TextPrimary = Color(0xFF1A1A1A)

// 로고 그라디언트 (Figma: from #FF7E86 → to #FFB47A, horizontal)
val LogoGradientStart = Color(0xFFFF7E86)
val LogoGradientEnd = Color(0xFFFFB47A)
val LogoGradient: Brush = Brush.horizontalGradient(
    colors = listOf(LogoGradientStart, LogoGradientEnd),
)

// 카카오 로그인 버튼
val KakaoYellow = Color(0xFFFEE500)
val KakaoLabel = Color(0xFF3C1E1E)
