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
import androidx.compose.runtime.LaunchedEffect
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
 *  - 캘린더 연동: Outlook 연결 · 해제 (#242) — 회사 Outlook 의 재택 일정을 가져온다
 *  - 계정: 로그아웃 — 탭하면 확인 알림 (Figma 4407:80805) 후 실행
 *
 * 최신 버전이 현재보다 높으면 파란 굵은 글씨 + "새 버전이 나왔어요" 안내, 같거나 낮으면 회색 + 초록 점
 * "최신 버전을 사용하고 있어요". 조회 중 · 실패면 값만 `…` / `-` 로 두고 안내는 숨긴다.
 *
 * @param ui 화면 상태.
 * @param outlook Outlook 연동 상태.
 * @param onBack 뒤로가기.
 * @param onReleaseNotesClick 릴리즈 노트 행 탭.
 * @param onOutlookConnect 미연결 상태에서 Outlook 행 탭.
 * @param onOutlookNameChange 팀 캘린더 이름 시트에서 저장.
 * @param onOutlookDisconnect 연결 해제 확인 알림에서 "연결 해제" 를 눌렀을 때.
 * @param onLogout 로그아웃 확인 알림에서 "로그아웃" 을 눌렀을 때.
 */
@Composable
fun MoreScreen(
    ui: MoreUiState,
    outlook: OutlookLinkState,
    onBack: () -> Unit,
    onReleaseNotesClick: () -> Unit,
    onOutlookConnect: () -> Unit,
    onOutlookNameChange: (String) -> Unit,
    onOutlookDisconnect: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var nameSheet by remember { mutableStateOf(false) }
    // 연결 직후 이름이 없으면 바로 입력 시트를 띄운다 — 이름이 있어야 재택을 가져올 수 있음.
    LaunchedEffect(outlook.email, outlook.teamName) {
        if (outlook.email != null && outlook.teamName == null) nameSheet = true
    }
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
            Section(label = "캘린더 연동") {
                OutlookCard(
                    outlook = outlook,
                    onConnect = onOutlookConnect,
                    onEditName = { nameSheet = true },
                    onDisconnect = { confirmDisconnect = true },
                )
                Hint(
                    text = outlook.error ?: when {
                        outlook.email != null && outlook.teamName == null ->
                            "팀 캘린더에 쓰는 이름을 입력하면 내 재택 일정을 가져와요."
                        else -> "TeamAIVN 캘린더에서 내 재택 일정을 가져와 함께 보여줘요."
                    },
                    color = if (outlook.error != null) DestructiveRed else TextSecondary,
                )
            }
            Section(label = "계정") {
                Card {
                    MenuRow(label = "로그아웃", labelColor = DestructiveRed, onClick = { confirmLogout = true })
                }
            }
        }
    }
    OutlookNameSheet(
        visible = nameSheet,
        initial = outlook.teamName.orEmpty(),
        onDismiss = { nameSheet = false },
        onConfirm = {
            nameSheet = false
            onOutlookNameChange(it)
        },
    )
    if (confirmDisconnect) {
        AppAlertDialog(
            title = "Outlook 연결을 해제할까요?",
            message = "가져온 재택 일정이 캘린더에서 사라져요.\nOutlook 원본 일정은 그대로 남아요.",
            actions = listOf(
                AlertAction("연결 해제", AlertActionStyle.DestructiveText, onClick = {
                    confirmDisconnect = false
                    onOutlookDisconnect()
                }),
                AlertAction("취소하기", AlertActionStyle.Cancel, onClick = { confirmDisconnect = false }),
            ),
            onDismissRequest = { confirmDisconnect = false },
            stacked = true,
        )
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

/**
 * Outlook 연동 카드 (#242). 미연결: "Outlook 캘린더 · 연결", 연결됨: 계정 이메일 · 팀 캘린더 이름 ·
 * "연결 해제" 행. 진행 중엔 값 자리에 `…` 를 두고 탭을 막는다.
 */
@Composable
private fun OutlookCard(
    outlook: OutlookLinkState,
    onConnect: () -> Unit,
    onEditName: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card {
        val email = outlook.email
        if (email == null) {
            MenuRow(
                label = "Outlook 캘린더",
                onClick = onConnect.takeUnless { outlook.busy },
                trailing = {
                    if (outlook.busy) ValueText("…") else ValueText("연결", color = PrimaryBlue, weight = FontWeight.SemiBold)
                },
            )
        } else {
            MenuRow(
                label = "Outlook 캘린더",
                trailing = { ValueText(if (outlook.busy) "…" else email) },
            )
            CardDividerLine()
            MenuRow(
                label = "팀 캘린더 이름",
                onClick = onEditName.takeUnless { outlook.busy },
                trailing = {
                    val name = outlook.teamName
                    if (name == null) ValueText("입력", color = PrimaryBlue, weight = FontWeight.SemiBold) else ValueText(name)
                },
            )
            CardDividerLine()
            MenuRow(
                label = "연결 해제",
                labelColor = DestructiveRed,
                onClick = onDisconnect.takeUnless { outlook.busy },
            )
        }
    }
}

/**
 * 카드 아래 한 줄 안내.
 *
 * @param text 문구.
 * @param color 글자 색.
 */
@Composable
private fun Hint(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        style = TextStyle(
            fontFamily = LocalPretendardFontFamily.current,
            fontSize = 12.sp,
            color = color,
        ),
    )
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
            outlook = OutlookLinkState(),
            onBack = {},
            onReleaseNotesClick = {},
            onOutlookConnect = {},
            onOutlookNameChange = {},
            onOutlookDisconnect = {},
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
            outlook = OutlookLinkState(email = "me@example.com", teamName = "양현진"),
            onBack = {},
            onReleaseNotesClick = {},
            onOutlookConnect = {},
            onOutlookNameChange = {},
            onOutlookDisconnect = {},
            onLogout = {},
        )
    }
}
