package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_left
import org.jetbrains.compose.resources.painterResource

/**
 * iOS 26 톤 상단 앱바. 좌측 원형 리퀴드 글래스 뒤로가기 버튼 + 중앙 타이틀 + (옵션) 우측 슬롯.
 *
 * @param title 중앙 타이틀 문자열.
 * @param onBack 뒤로가기 버튼 탭 콜백.
 * @param modifier 외부 [Modifier].
 * @param trailing 우측 정렬 액션 슬롯. 저장 · 완료 등 텍스트 버튼이나 아이콘 버튼 배치용. null 이면 생략.
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
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
        if (trailing != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                trailing()
            }
        }
    }
}

/**
 * iOS 26 리퀴드 글래스 원형 뒤로가기 버튼. [Modifier.liquidGlass] 로 유리 서피스를 그리고 중앙에
 * 셰브론을 얹는다.
 *
 * @param onClick 탭 콜백.
 * @param modifier 외부 [Modifier].
 * @param diameter 원 지름 (기본 36dp).
 */
@Composable
fun BackCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .liquidGlass(shape = CircleShape)
            // clip 을 clickable 앞에 둬야 ripple/indication 도 원형으로 잘린다. 없으면 사각형 bounds.
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_chevron_left),
            contentDescription = "뒤로가기",
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(36.dp),
        )
    }
}

// 프리뷰 배경은 리퀴드 글래스 톤이 잘 드러나는 iOS 26 계열 그라디언트로. 뒤 색이 흰색 알파 fill
// 을 통해 은은히 비쳐야 유리 느낌이 살아난다. drawBackdrop 을 쓰지 않으므로 preview 재귀 크래시가
// 재현되지 않는다.
private val PreviewBackground: Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFB199),
        Color(0xFFFF77A9),
        Color(0xFF9F6FFF),
    ),
)

@Preview
@Composable
private fun AppTopBarPreview() {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(PreviewBackground),
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
                .background(PreviewBackground),
            contentAlignment = Alignment.Center,
        ) {
            BackCircleButton(onClick = {})
        }
    }
}
