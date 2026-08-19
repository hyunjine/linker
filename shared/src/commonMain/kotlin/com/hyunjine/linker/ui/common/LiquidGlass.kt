package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 26 리퀴드 글래스 룩얼라이크 프리셋. Kyant `drawBackdrop` 이 유발하는 SkDrawable 재귀 사이클
 * (layoutlib · 실기기 양쪽에서 SIGSEGV) 을 피하기 위해 표준 Compose API 로만 구성한다.
 */
object LiquidGlassDefaults {
    /** 유리 상단 하이라이트 색 (알파 큰 흰색). */
    val HighlightTop: Color = Color.White.copy(alpha = 0.85f)
    /** 유리 하단 톤. 위/아래 미묘한 그라디언트로 굴절 하이라이트 느낌. */
    val HighlightBottom: Color = Color.White.copy(alpha = 0.55f)
    /** 유리 edge highlight (얇은 흰색 스트로크). */
    val BorderColor: Color = Color.White.copy(alpha = 0.6f)
    val BorderWidth: Dp = 0.5.dp
}

/**
 * iOS 26 리퀴드 글래스 스타일을 임의 요소에 얹는 Modifier. 세로 흰색 알파 그라디언트 fill +
 * 얇은 흰색 border 조합으로 유리 시각 톤을 근사한다.
 *
 * 실제 배경 블러는 표준 Compose 만으로 배경 요소에 소급 적용할 수 없어 생략한다. 반투명 fill 이
 * 배경 색을 은은히 비춰 리퀴드 글래스 톤을 낸다. blur/lens/vibrancy 가 꼭 필요하면 별도 라이브러리
 * (Kyant backdrop 등) 를 고려해야 하지만, 현재 그 라이브러리는 preview · 실기기 모두에서 재귀 사이클
 * 크래시가 재현되어 이 프로젝트에서는 사용하지 않는다.
 *
 * 소프트 섀도우는 의도적으로 걸지 않는다. `Modifier.shadow(clip = false)` 를 붙이면 iOS Skia 에서
 * 반투명 fill 뒤로 shape 원본 실루엣이 그대로 그려져 안쪽에 이중 원 artefact 가 남는다.
 *
 * @param shape 유리 서피스 형태. 기본 [CircleShape].
 * @param fill 유리 fill 브러시. 기본 상단→하단 흰색 알파 그라디언트.
 * @param borderColor 유리 edge highlight 색.
 * @param borderWidth 유리 edge highlight 두께.
 */
fun Modifier.liquidGlass(
    shape: Shape = CircleShape,
    fill: Brush = Brush.verticalGradient(
        colors = listOf(LiquidGlassDefaults.HighlightTop, LiquidGlassDefaults.HighlightBottom),
    ),
    borderColor: Color = LiquidGlassDefaults.BorderColor,
    borderWidth: Dp = LiquidGlassDefaults.BorderWidth,
): Modifier = this
    .background(brush = fill, shape = shape)
    .border(width = borderWidth, color = borderColor, shape = shape)
