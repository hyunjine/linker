package com.hyunjine.linker.ui.common

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.TextPrimary
import com.kyant.backdrop.Backdrop
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_left
import org.jetbrains.compose.resources.painterResource

/**
 * iOS 26 톤 상단 앱바. 좌측 원형 리퀴드 글래스 뒤로가기 버튼 + 중앙 타이틀.
 *
 * @param title 중앙 타이틀 문자열.
 * @param onBack 뒤로가기 버튼 탭 콜백.
 * @param backdrop 뒤로가기 버튼의 유리가 샘플링할 배경 [Backdrop].
 * 상위 스캐폴드에서 `rememberLayerBackdrop()` 로 만든 인스턴스를 넘기고, 배경/콘텐츠에
 * `Modifier.layerBackdrop(backdrop)` 을 붙여야 유리 효과가 실제로 보인다.
 * @param modifier 외부 [Modifier].
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    backdrop: Backdrop,
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
            backdrop = backdrop,
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
 * iOS 26 리퀴드 글래스 원형 뒤로가기 버튼. [Modifier.liquidGlass] 로 배경을 샘플링한 유리
 * 서피스를 그리고 중앙에 셰브론을 얹는다.
 *
 * @param onClick 탭 콜백.
 * @param backdrop 유리가 샘플링할 배경 [Backdrop].
 * @param modifier 외부 [Modifier].
 * @param diameter 원 지름 (기본 36dp).
 */
@Composable
fun BackCircleButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .liquidGlass(backdrop = backdrop, shape = CircleShape)
            .size(diameter)
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

// Preview 는 의도적으로 두지 않는다. Kyant backdrop 의 drawBackdrop 은 layoutlib(Compose Preview
// 렌더러) 의 RenderNode 트리 캡처와 상호작용해 prepareTreeImpl 이 무한 재귀에 빠지고 스택 오버플로우
// 로 스튜디오가 통째로 크래시한다. layerBackdrop provider 와 유리 소비자를 형제로 배치해도, tint
// 폴백 modifier 로 우회해도 실기기 코드와 다른 경로가 되므로 재현이 불안정하다. 유리 톤/사이즈는
// 실기기에서 확인한다.
