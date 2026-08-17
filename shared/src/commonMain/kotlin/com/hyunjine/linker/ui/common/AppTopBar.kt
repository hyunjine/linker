package com.hyunjine.linker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.TextPrimary

/**
 * iOS 26 톤 상단 앱바. 좌측 원형 뒤로가기 버튼 + 중앙 타이틀.
 *
 * @param title 중앙 타이틀 문자열.
 * @param onBack 뒤로가기 버튼 탭 콜백.
 * @param modifier 외부 [Modifier].
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BackCircleButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            style = TextStyle(
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

/**
 * iOS 26 스타일 원형 뒤로가기 버튼. 프로스트한 흰 반투명 배경 위에 상단 하이라이트
 * 그라디언트 보더와 소프트 드롭 섀도우를 얹어 유리 버튼 톤을 재현한다.
 *
 * @param onClick 탭 콜백.
 * @param modifier 외부 [Modifier].
 * @param diameter 원 지름 (기본 36dp).
 */
@Composable
fun BackCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 36.dp,
) {
    // 상단이 더 밝고 하단이 살짝 어두운 세로 그라디언트 — iOS 26 유리 버튼 특유의 톤.
    val topHighlight = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.9f),
        0.55f to Color.White.copy(alpha = 0.35f),
        1f to Color.White.copy(alpha = 0.15f),
    )
    Box(
        modifier = modifier
            .size(diameter)
            .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
//            .background(Color.White.copy(alpha = 0.55f))
//            .border(width = 0.6.dp, brush = topHighlight, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.3f, h * 0.5f)
                    lineTo(w * 0.65f, h * 0.85f)
                },
                color = TextPrimary,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun AppTopBarPreview() {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFFF2F2F7)),
        ) {
            AppTopBar(
                title = "프로필 편집",
                onBack = {},
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Preview
@Composable
private fun BackCircleButtonPreview() {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFFF2F2F7)),
            contentAlignment = Alignment.Center,
        ) {
            BackCircleButton(onClick = {})
        }
    }
}