package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.TextTertiary

/**
 * iOS 스타일의 카드 위 섹션 라벨 (13sp 회색). 리스트나 폼 그룹 상단에 놓인다.
 *
 * @param text 라벨 문자열.
 * @param modifier 외부 [Modifier].
 * @param horizontalPadding 좌우 인셋 (기본 32dp — iOS 리스트 정렬 규칙).
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
) {
    val font = LocalPretendardFontFamily.current
    Row(modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        Text(
            text = text,
            style = TextStyle(
                color = TextTertiary,
                fontSize = 13.sp,
                fontFamily = font,
            ),
        )
    }
}

@Composable
@Preview(showBackground = true)
fun SectionLabelPreview() {
    ProvidePretendard {
        SectionLabel(
            text = "hi"
        )
    }
}
