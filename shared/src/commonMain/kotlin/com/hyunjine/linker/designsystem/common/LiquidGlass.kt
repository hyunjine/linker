package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
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

    /**
     * 유리 rim (border) 브러시. 좌상단에서 사선으로 햇빛이 비치는 톤:
     *   - 0% (좌상단): 흰색 알파 0.95 - 광원 쪽 하이라이트
     *   - 45% (중간): 흰색 알파 0.05 - rim 이 거의 사라지며 뒤 색이 비침
     *   - 100% (우하단): 흰색 알파 0.7 - 반대편 반사광 (테두리 유리 안쪽 반사)
     * 기본 `Brush.linearGradient(colors)` 의 start/end 가 (0,0)→(Infinity,Infinity) 라 대각선.
     */
    val BorderBrush: Brush = Brush.linearGradient(
        0.00f to Color.White.copy(alpha = 0.95f),
        0.45f to Color.White.copy(alpha = 0.05f),
        1.00f to Color.White.copy(alpha = 0.70f),
    )

    /** 그라디언트 하이라이트가 잘 보이도록 살짝 두껍게. */
    val BorderWidth: Dp = 1.dp
}

/**
 * iOS 26 리퀴드 글래스 스타일을 임의 요소에 얹는 Modifier. 세로 흰색 알파 그라디언트 fill +
 * 사선 광원 하이라이트 border 조합으로 유리 시각 톤을 근사한다.
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
 * @param borderBrush 유리 rim 하이라이트 브러시. 기본 좌상→우하 사선 광원.
 * @param borderWidth 유리 rim 두께.
 */
fun Modifier.liquidGlass(
    shape: Shape = CircleShape,
    fill: Brush = Brush.verticalGradient(
        colors = listOf(LiquidGlassDefaults.HighlightTop, LiquidGlassDefaults.HighlightBottom),
    ),
    borderBrush: Brush = LiquidGlassDefaults.BorderBrush,
    borderWidth: Dp = LiquidGlassDefaults.BorderWidth,
): Modifier = this
    .background(brush = fill, shape = shape)
    .border(width = borderWidth, brush = borderBrush, shape = shape)

// ---------- Previews ----------

// 리퀴드 글래스는 뒤 배경이 은은히 비쳐야 톤이 살아나므로 컬러풀 그라디언트 위에 얹어 프리뷰.
private val PreviewBackground: Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFB199),
        Color(0xFFFF77A9),
        Color(0xFF9F6FFF),
    ),
)

@Preview
@Composable
private fun LiquidGlassPreview_Shapes() {
    Column(
        modifier = Modifier
            .size(320.dp, 200.dp)
            .background(PreviewBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 원형 — BackCircleButton 등에서 쓰는 형태
        Box(
            modifier = Modifier.size(72.dp).liquidGlass(shape = CircleShape),
        )
        // 라운드 사각형 — 카드/버튼 서피스에 적용하는 형태
        Box(
            modifier = Modifier.fillMaxSize().liquidGlass(shape = RoundedCornerShape(18.dp)),
        )
    }
}

@Preview
@Composable
private fun LiquidGlassPreview_Pill() {
    Box(
        modifier = Modifier
            .size(240.dp, 80.dp)
            .background(PreviewBackground)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(180.dp, 44.dp)
                .liquidGlass(shape = RoundedCornerShape(22.dp)),
        )
    }
}
