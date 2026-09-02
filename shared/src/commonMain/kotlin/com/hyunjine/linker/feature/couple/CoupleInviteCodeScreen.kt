package com.hyunjine.linker.feature.couple

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.SectionLabel
import com.hyunjine.linker.designsystem.theme.Chevron
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.Separator
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 내 초대코드 발급 · 공유 · 이미 조인된 경우 안내 화면.
 *
 * 세 상태를 [state] 로 분기:
 *  - [InviteCodeUiState.Loading]: 로딩 placeholder ("…").
 *  - [InviteCodeUiState.Solo]: 코드 카드 + 공유 액션.
 *  - [InviteCodeUiState.Paired]: 코드 대신 "이미 파트너 있음" 안내.
 *
 * @param onBack 좌상단 back.
 * @param onCopy Solo 상태에서 "내 초대코드" 행 탭 → 클립보드 복사.
 * @param onShare Solo 상태에서 "공유하기" 행 탭 → 시스템 공유 시트.
 */
@Composable
fun CoupleInviteCodeScreen(
    state: InviteCodeUiState = InviteCodeUiState.Loading,
    onBack: () -> Unit = {},
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
) {
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
            when (state) {
                is InviteCodeUiState.Paired -> PairedContent()
                is InviteCodeUiState.Loading -> InviteCodeContent(code = "…", onCopy = onCopy, onShare = onShare)
                is InviteCodeUiState.Solo -> InviteCodeContent(code = state.code, onCopy = onCopy, onShare = onShare)
            }
            Spacer(Modifier.weight(1f))
        }

        AppTopBar(
            title = "내 초대코드",
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun InviteCodeContent(code: String, onCopy: () -> Unit, onShare: () -> Unit) {
    DescriptionText(
        text = "아래 코드를 상대에게 공유하면\n상대가 입력해 연결할 수 있습니다.",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
    Spacer(Modifier.height(24.dp))

    SectionLabel(text = "내 초대코드", horizontalPadding = 20.dp)
    Spacer(Modifier.height(8.dp))
    MyCodeCard(
        code = code,
        onCopy = onCopy,
        onShare = onShare,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** 이미 파트너와 연결된 상태 안내. 코드 · 공유 액션은 감춤. */
@Composable
private fun PairedContent() {
    val font = LocalPretendardFontFamily.current
    Spacer(Modifier.height(24.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "이미 파트너와 연결됨",
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = TextPrimary,
            ),
        )
        Text(
            text = "새 초대코드는 파트너 연결이 해제된 후에만 발급받을 수 있습니다.",
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = font,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = TextSecondary,
            ),
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

/**
 * "내 초대코드" 카드. iOS grouped list 톤:
 *   흰 배경 + 18dp radius, 두 행 사이 0.5dp #C6C6C8 세퍼레이터 (좌측 16dp inset)
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
        CardRow(label = "내 초대코드", trailingText = code, onClick = onCopy)
        InsetSeparator()
        CardRow(label = "공유하기", trailingText = null, onClick = onShare)
    }
}

@Composable
private fun CardRow(label: String, trailingText: String?, onClick: () -> Unit) {
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

@Preview
@Composable
private fun CoupleInviteCodeScreenPreview_Solo() {
    ProvidePretendard { CoupleInviteCodeScreen(state = InviteCodeUiState.Solo("AB12CD")) }
}

@Preview
@Composable
private fun CoupleInviteCodeScreenPreview_Loading() {
    ProvidePretendard { CoupleInviteCodeScreen(state = InviteCodeUiState.Loading) }
}

@Preview
@Composable
private fun CoupleInviteCodeScreenPreview_Paired() {
    ProvidePretendard { CoupleInviteCodeScreen(state = InviteCodeUiState.Paired) }
}
