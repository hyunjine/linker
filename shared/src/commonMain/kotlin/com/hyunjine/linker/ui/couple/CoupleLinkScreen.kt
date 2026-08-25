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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.AppTopBar
import com.hyunjine.linker.ui.common.PrimaryButton
import com.hyunjine.linker.ui.common.SectionLabel
import com.hyunjine.linker.ui.theme.Chevron
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.Separator
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary

// Figma (2672:59198) 기준 iOS grouped 스타일:
//   - 배경 SurfaceGray (F2F2F7), 카드는 흰색 rounded 18dp
//   - 상단 AppTopBar (좌측 유리 back circle + 중앙 타이틀). 하단 CTA 는 PrimaryButton
//   - AppTopBar 는 오버레이로 띄우고 콘텐츠는 TOP_BAR_HEIGHT 만큼 위쪽 여백을 준다

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 커플 연결 화면. 내 초대코드를 상대에게 공유하거나 상대 초대코드를 입력해 두 계정을 잇는다.
 *
 * @param myCode 서버가 발급한 내 초대코드. null 이면 아직 로딩 중.
 * @param linking 연결 요청 진행 중 여부. true 면 CTA 비활성 · 문구 변경.
 * @param onBack 좌측 상단 원형 back 탭 콜백.
 * @param onCopyMyCode "내 초대코드" 행 탭 시 호출 (보통 클립보드 복사).
 * @param onShareMyCode "공유하기" 행 탭 시 호출 (시스템 공유 시트 오픈에 위임).
 * @param onLink 하단 "연결하기" 탭 시 호출. 사용자가 입력한 상대 코드 전달.
 */
@Composable
fun CoupleLinkScreen(
    myCode: String? = "ABC123",
    linking: Boolean = false,
    onBack: () -> Unit = {},
    onCopyMyCode: () -> Unit = {},
    onShareMyCode: () -> Unit = {},
    onLink: (partnerCode: String) -> Unit = {},
) {
    var partnerCode by rememberSaveable { mutableStateOf("") }
    val canLink by remember(linking) { derivedStateOf { !linking && partnerCode.isNotBlank() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // 상단 오버레이 앱바 자리 확보.
            Spacer(Modifier.height(TOP_BAR_HEIGHT))

            DescriptionText(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel(text = "내 초대코드", horizontalPadding = 20.dp)
            Spacer(Modifier.height(8.dp))
            MyCodeCard(
                code = myCode ?: "…",
                onCopy = onCopyMyCode,
                onShare = onShareMyCode,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel(text = "상대방 초대코드", horizontalPadding = 20.dp)
            Spacer(Modifier.height(8.dp))
            PartnerCodeInputCard(
                value = partnerCode,
                onValueChange = { partnerCode = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = if (linking) "연결 중…" else "연결하기",
                onClick = { if (canLink) onLink(partnerCode.trim()) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            )
        }

        // 상단 오버레이 앱바.
        AppTopBar(
            title = "커플 연결",
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun DescriptionText(modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    // Figma: labels/secondary rgba(60,60,67,0.6), 15sp, lineHeight 1.35
    Text(
        text = "상대방의 초대코드를 입력하거나\n내 코드를 상대에게 공유하세요.",
        modifier = modifier,
        style = TextStyle(
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontFamily = font,
        ),
    )
}

/**
 * "내 초대코드" 카드. iOS grouped list 톤:
 *   - 흰 배경 + 18dp radius, 두 행 사이 0.5dp #C6C6C8 세퍼레이터 (좌측 16dp inset)
 *   - Row height 52dp, 17sp 텍스트
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
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard),
    ) {
        CardRow(
            label = "내 초대코드",
            trailingText = code,
            onClick = onCopy,
        )
        InsetSeparator()
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
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
        )
        Spacer(Modifier.weight(1f))
        if (trailingText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = trailingText,
                    style = TextStyle(color = TextSecondary, fontSize = 17.sp, fontFamily = font),
                )
                Text(
                    text = "›",
                    style = TextStyle(
                        color = Chevron,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InsetSeparator() {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Separator),
        )
    }
}

/**
 * 상대방 초대코드 입력 카드. 단일 행 흰 카드에 라벨 + 텍스트 필드.
 * 빈 값이면 회색 placeholder ("ABC123") 를 겹쳐 보여준다.
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
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "초대코드",
            modifier = Modifier.width(100.dp),
            style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
        )
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "ABC123",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 17.sp,
                        fontFamily = font,
                    ),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
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
