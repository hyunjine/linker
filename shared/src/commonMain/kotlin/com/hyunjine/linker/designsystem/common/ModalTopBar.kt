package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.TextPrimary

/**
 * iOS 26 모달 편집 화면용 상단 툴바. 좌측 텍스트 버튼(취소류) / 중앙 타이틀 / 우측 텍스트 버튼(저장류).
 * 흰 배경 위에 얹혀 border 없이 순수 텍스트로만 구성된다.
 *
 * @param title 중앙 타이틀 문자열.
 * @param leadingText 좌측 버튼 라벨. 관례상 "취소" — Regular weight.
 * @param onLeadingClick 좌측 버튼 탭 콜백.
 * @param trailingText 우측 버튼 라벨. 관례상 "저장" / "완료" — SemiBold weight.
 * @param onTrailingClick 우측 버튼 탭 콜백.
 * @param trailingEnabled 우측 버튼 활성화. `false` 이면 회색 톤이 되고 클릭이 무시된다.
 * @param modifier 외부 [Modifier].
 */
@Composable
fun ModalTopBar(
    title: String,
    leadingText: String,
    onLeadingClick: () -> Unit,
    trailingText: String,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingEnabled: Boolean = true,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = leadingText,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onLeadingClick),
            style = TextStyle(
                color = PrimaryBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
            ),
        )
        Text(
            text = title,
            style = TextStyle(
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
        Text(
            text = trailingText,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(enabled = trailingEnabled, onClick = onTrailingClick),
            style = TextStyle(
                // 비활성 상태일 때 절반 톤으로 흐리게 표시. 완전 회색 대신 alpha 로 낮춰 iOS 톤 유지.
                color = if (trailingEnabled) PrimaryBlue else PrimaryBlue.copy(alpha = 0.3f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Preview
@Composable
fun test() {
    Column {
        Text(text ="hi")
    }
}