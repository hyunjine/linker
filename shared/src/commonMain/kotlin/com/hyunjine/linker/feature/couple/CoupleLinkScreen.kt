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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.tooling.preview.Preview
import com.hyunjine.linker.ui.common.AppTopBar
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 커플 연결 진입 chooser. 두 옵션 중 하나로 분기:
 *  - "내 초대코드 만들기" → [CoupleInviteCodeScreen] (내 커플 자동 생성 + 코드 공유)
 *  - "상대 코드로 연결" → [CoupleJoinScreen] (파트너 코드 입력 · 자기 커플은 안 만듦)
 *
 * 이렇게 분리해 "상대 코드 입력만 하러 온 유저" 가 자기 커플을 만들지 않아 orphan 발생 감소.
 */
@Composable
fun CoupleLinkScreen(
    onBack: () -> Unit = {},
    onCreateInvite: () -> Unit = {},
    onEnterPartnerCode: () -> Unit = {},
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
            Description(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
            ChoiceCard(
                title = "내 초대코드 만들기",
                subtitle = "코드를 상대에게 공유해 연결합니다",
                onClick = onCreateInvite,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            ChoiceCard(
                title = "상대 코드로 연결하기",
                subtitle = "상대가 준 6자 코드를 입력해 연결합니다",
                onClick = onEnterPartnerCode,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.weight(1f))
        }

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
private fun Description(modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    Text(
        text = "상대에게 내 코드를 공유하거나\n상대의 코드로 연결하세요.",
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
private fun ChoiceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
            Text(
                text = subtitle,
                style = TextStyle(
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = font,
                ),
            )
        }
        Text(
            text = "›",
            style = TextStyle(
                color = TextSecondary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
            ),
        )
    }
}

@Preview
@Composable
private fun CoupleLinkScreenPreview() {
    ProvidePretendard { CoupleLinkScreen() }
}
