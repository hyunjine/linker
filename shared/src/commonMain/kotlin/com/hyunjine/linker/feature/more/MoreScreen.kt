package com.hyunjine.linker.feature.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AlertAction
import com.hyunjine.linker.designsystem.common.AlertActionStyle
import com.hyunjine.linker.designsystem.common.AppAlertDialog
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.theme.CardDivider
import com.hyunjine.linker.designsystem.theme.Chevron
import com.hyunjine.linker.designsystem.theme.DestructiveRed
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.StatusOkGreen
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

private val CardShape = RoundedCornerShape(16.dp)

/**
 * 더보기 화면 (#385 · Figma 4401:80455). 드로워에서 뺀 부가 항목을 모은다.
 *
 *  - 앱 정보: 릴리즈 노트 `›` · 현재 버전 · 최신 버전 (+ 상태 안내)
 *  - 계정: 로그아웃 — 탭하면 확인 알림 (Figma 4407:80805) 후 실행
 *
 * 최신 버전이 현재보다 높으면 파란 굵은 글씨 + "새 버전이 나왔어요" 안내, 같거나 낮으면 회색 + 초록 점
 * "최신 버전을 사용하고 있어요". 조회 중 · 실패면 값만 `…` / `-` 로 두고 안내는 숨긴다.
 *
 * @param ui 화면 상태.
 * @param onBack 뒤로가기.
 * @param onReleaseNotesClick 릴리즈 노트 행 탭.
 * @param onLogout 로그아웃 확인 알림에서 "로그아웃" 을 눌렀을 때.
 */
@Composable
fun MoreScreen(
    ui: MoreUiState,
    onBack: () -> Unit,
    onReleaseNotesClick: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AppTopBar(title = "더보기", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(label = "앱 정보") {
                Card {
                    MenuRow(label = "릴리즈 노트", onClick = onReleaseNotesClick, trailing = { ChevronIcon() })
                    CardDividerLine()
                    MenuRow(label = "현재 버전", trailing = { ValueText("v${ui.currentVersion}") })
                    CardDividerLine()
                    MenuRow(label = "최신 버전", trailing = { LatestValue(ui) })
                }
                VersionHint(ui)
            }
            Section(label = "계정") {
                Card {
                    MenuRow(label = "로그아웃", labelColor = DestructiveRed, onClick = { confirmLogout = true })
                }
            }
        }
    }
    if (confirmLogout) {
        LogoutConfirmDialog(
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/** 로그아웃 확인 (Figma 4407:80805 · Apple iOS 26 키트 Stacked 알림) — 위 "로그아웃" · 아래 "취소하기". */
@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        title = "로그아웃할까요?",
        message = "다시 로그인하면 일정과 설정을\n그대로 이용할 수 있어요.",
        actions = listOf(
            AlertAction("로그아웃", AlertActionStyle.DestructiveText, onClick = onConfirm),
            AlertAction("취소하기", AlertActionStyle.Cancel, onClick = onDismiss),
        ),
        onDismissRequest = onDismiss,
        stacked = true,
    )
}

@Composable
private fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = TextStyle(
                fontFamily = LocalPretendardFontFamily.current,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = TextSecondary,
            ),
        )
        content()
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceCard),
        content = content,
    )
}

/** 카드 한 줄. [onClick] 이 있으면 행 전체가 탭 타겟 (리플). */
@Composable
private fun MenuRow(
    label: String,
    labelColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = LocalPretendardFontFamily.current,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = labelColor,
            ),
        )
        trailing()
    }
}

/** 카드 내부 구분선 — 좌측 16dp 인셋. */
@Composable
private fun CardDividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(CardDivider),
    )
}

@Composable
private fun ChevronIcon() {
    Image(
        painter = painterResource(Res.drawable.ic_chevron_right),
        contentDescription = null,
        colorFilter = ColorFilter.tint(Chevron),
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun ValueText(text: String, color: Color = TextSecondary, weight: FontWeight = FontWeight.Normal) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = LocalPretendardFontFamily.current,
            fontWeight = weight,
            fontSize = 16.sp,
            color = color,
        ),
    )
}

@Composable
private fun LatestValue(ui: MoreUiState) {
    when (val latest = ui.latest) {
        LatestVersion.Loading -> ValueText("…")
        LatestVersion.Unavailable -> ValueText("-")
        is LatestVersion.Loaded ->
            if (ui.updateAvailable) {
                ValueText("v${latest.version}", color = PrimaryBlue, weight = FontWeight.SemiBold)
            } else {
                ValueText("v${latest.version}")
            }
    }
}

/** 카드 아래 한 줄 안내. 최신 버전을 모르면 표시하지 않는다. */
@Composable
private fun VersionHint(ui: MoreUiState) {
    if (ui.latest !is LatestVersion.Loaded) return
    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!ui.updateAvailable) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StatusOkGreen),
            )
        }
        Text(
            text = if (ui.updateAvailable) {
                "새 버전이 나왔어요. 업데이트하고 새 기능을 만나 보세요."
            } else {
                "최신 버전을 사용하고 있어요."
            },
            style = TextStyle(
                fontFamily = LocalPretendardFontFamily.current,
                fontSize = 12.sp,
                color = TextSecondary,
            ),
        )
    }
}

// ---------- Preview ----------

@Preview
@Composable
private fun MoreScreenUpdatePreview() {
    LinkerTheme {
        MoreScreen(
            ui = MoreUiState(currentVersion = "1.5.1", latest = LatestVersion.Loaded("1.5.2")),
            onBack = {},
            onReleaseNotesClick = {},
            onLogout = {},
        )
    }
}

@Preview
@Composable
private fun MoreScreenLatestPreview() {
    LinkerTheme {
        MoreScreen(
            ui = MoreUiState(currentVersion = "1.5.1", latest = LatestVersion.Loaded("1.5.1")),
            onBack = {},
            onReleaseNotesClick = {},
            onLogout = {},
        )
    }
}
