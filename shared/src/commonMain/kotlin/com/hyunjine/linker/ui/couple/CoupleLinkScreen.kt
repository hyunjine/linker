package com.hyunjine.linker.ui.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.ModalTopBar
import com.hyunjine.linker.ui.theme.Background
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.PlaceholderText
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SeparatorGrouped
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextTertiary

// Figma 참조 프레임 (ScheduleEdit — 2614:3206) 의 iOS 26 스타일 적용:
//   - 배경: 흰색, 카드도 흰색 (rounded 10dp) — 시각 분리는 얇은 세퍼레이터 (#D9D9DE, 0.5dp) 만으로.
//   - 상단 툴바: 취소 / 타이틀 / 연결 (모두 텍스트 버튼) — 별도 오버레이 없이 상단에 정렬.
//   - 폼 그룹 상하 간격은 16dp (Figma p-[16px] gap-[16px]).

/**
 * 커플 연결 화면 — iOS 26 모달 편집 스타일. 내 초대코드를 상대에게 공유하거나
 * 상대의 초대코드를 입력해 두 계정을 잇는다.
 *
 * @param myCode 로그인한 사용자의 초대코드. 서버 발급 값을 표시만.
 * @param onCancel 좌측 상단 "취소" 탭 시 호출. 저장 없이 화면 닫기.
 * @param onCopyMyCode 내 초대코드 행 탭 시 호출. 보통 클립보드 복사.
 * @param onShareMyCode "공유하기" 행 탭 시 호출. 시스템 공유 시트 오픈에 위임.
 * @param onLink 우측 상단 "연결" 탭 시 호출. 사용자가 입력한 상대 코드 전달.
 */
@Composable
fun CoupleLinkScreen(
    myCode: String = "ABC123",
    onCancel: () -> Unit = {},
    onCopyMyCode: () -> Unit = {},
    onShareMyCode: () -> Unit = {},
    onLink: (partnerCode: String) -> Unit = {},
) {
    var partnerCode by rememberSaveable { mutableStateOf("") }
    // 상대 코드가 비어있으면 우측 상단 "연결" 버튼 비활성. iOS 저장 버튼 관례.
    val canLink by remember { derivedStateOf { partnerCode.isNotBlank() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ModalTopBar(
            title = "커플 연결",
            leadingText = "취소",
            onLeadingClick = onCancel,
            trailingText = "연결",
            onTrailingClick = { onLink(partnerCode.trim()) },
            trailingEnabled = canLink,
        )

        // Figma: content column p-[16px] gap-[16px]. 폼 요소 간 균일한 16dp 세로 갭.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DescriptionText()

            MyCodeCard(
                code = myCode,
                onCopy = onCopyMyCode,
                onShare = onShareMyCode,
            )

            SectionLabel("상대방 초대코드")

            PartnerCodeInputCard(
                value = partnerCode,
                onValueChange = { partnerCode = it },
            )
        }
    }
}

@Composable
private fun DescriptionText() {
    val font = LocalPretendardFontFamily.current
    Text(
        text = "상대방의 초대코드를 입력하거나\n내 코드를 상대에게 공유하세요.",
        style = TextStyle(
            color = TextTertiary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFamily = font,
        ),
    )
}

@Composable
private fun SectionLabel(text: String) {
    val font = LocalPretendardFontFamily.current
    Text(
        text = text,
        style = TextStyle(color = TextTertiary, fontSize = 13.sp, fontFamily = font),
    )
}

/**
 * "내 초대코드" 카드. iOS 26 modal 스타일:
 *   - 흰 배경 + 10dp radius
 *   - 두 행 사이 0.5dp #D9D9DE 세퍼레이터 (좌측 인셋 없음 — ScheduleEdit 카드와 동일)
 *   - 행별 px 16, py 12 (15sp 텍스트)
 */
@Composable
private fun MyCodeCard(
    code: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard),
    ) {
        CardRow(
            label = "내 초대코드",
            trailingText = code,
            onClick = onCopy,
        )
        CardSeparator()
        CardRow(
            label = "공유하기",
            trailingText = null,
            onClick = onShare,
        )
    }
}

@Composable
private fun CardRow(
    label: String,
    trailingText: String?,
    onClick: () -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = font),
        )
        Spacer(Modifier.weight(1f))
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = TextStyle(color = TextTertiary, fontSize = 15.sp, fontFamily = font),
            )
        }
    }
}

@Composable
private fun CardSeparator() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(SeparatorGrouped),
    )
}

/**
 * 상대방 초대코드 입력 카드. 단일 행짜리 흰 카드에 텍스트 필드 하나.
 * Figma: 값이 비어있으면 회색 placeholder (#999). 카드는 rounded 10dp.
 */
@Composable
private fun PartnerCodeInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "초대코드",
            modifier = Modifier.width(88.dp),
            style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = font),
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "ABC123",
                    style = TextStyle(
                        color = PlaceholderText,
                        fontSize = 15.sp,
                        fontFamily = font,
                    ),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, fontFamily = font),
                singleLine = true,
                cursorBrush = SolidColor(PrimaryBlue),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(),
            )
        }
    }
}

@Preview
@Composable
private fun CoupleLinkScreenPreview() {
    ProvidePretendard {
        CoupleLinkScreen()
    }
}
