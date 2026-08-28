package com.hyunjine.linker.feature.couple

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.PrimaryButton
import com.hyunjine.linker.designsystem.common.SectionLabel
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 상대 초대코드 입력 전용 화면. 파트너로부터 받은 코드로 이미 존재하는 커플에 합류.
 * 내 자기 커플은 생성하지 않음 (`InviteCodeScreen` 진입 시에만 자동 생성됨).
 *
 * @param linking 연결 API 진행 중 여부. true 면 CTA 비활성 + 문구 변경.
 * @param onBack 좌상단 back.
 * @param onLink 하단 "연결하기" 탭 → 입력한 상대 코드 전달.
 */
@Composable
fun CoupleJoinScreen(
    linking: Boolean = false,
    onBack: () -> Unit = {},
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
            Spacer(Modifier.height(TOP_BAR_HEIGHT))
            DescriptionText(
                text = "상대가 공유한 초대코드를 입력해\n두 계정을 연결합니다.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
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

        AppTopBar(
            title = "코드로 연결",
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun DescriptionText(text: String, modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontFamily = font,
        ),
    )
}

@Composable
private fun PartnerCodeInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    com.hyunjine.linker.designsystem.common.AppInputCard(
        label = "초대코드",
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = "ABC123",
        capitalization = KeyboardCapitalization.Characters,
    )
}

@Preview
@Composable
private fun CoupleJoinScreenPreview_Idle() {
    ProvidePretendard { CoupleJoinScreen(linking = false) }
}

@Preview
@Composable
private fun CoupleJoinScreenPreview_Linking() {
    ProvidePretendard { CoupleJoinScreen(linking = true) }
}
