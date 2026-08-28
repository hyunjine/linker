package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.OnPrimary
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SurfaceGray

/**
 * 화면 하단 CTA 로 쓰는 주 액션 버튼. iOS 톤의 채워진 파랑 필 버튼.
 *
 * @param text 버튼 라벨.
 * @param onClick 탭 콜백.
 * @param modifier 외부 [Modifier]. 폭은 기본 [fillMaxWidth].
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryBlue)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = OnPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Preview
@Composable
private fun PrimaryButtonPreview() {
    ProvidePretendard {
        Column(
            modifier = Modifier.fillMaxWidth().background(SurfaceGray).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text = "다음", onClick = {})
            PrimaryButton(text = "저장", onClick = {})
        }
    }
}
