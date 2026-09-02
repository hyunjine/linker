package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary

/** iOS 26 톤 alert 액션. 버튼은 capsule 형태. */
data class AlertAction(
    val label: String,
    val style: AlertActionStyle = AlertActionStyle.Default,
    val onClick: () -> Unit,
)

enum class AlertActionStyle {
    /** 주요 진행 액션 — 진한 fill (앱 primary) + 흰 텍스트. */
    Default,

    /** 취소 · 뒤로 — 밝은 회색 fill + 어두운 텍스트. */
    Cancel,

    /** 되돌릴 수 없는 액션 — 빨간 fill + 흰 텍스트. */
    Destructive,
}

private val IosRed = Color(0xFFFF3B30)
private val IosSecondaryBg = Color(0xFFE5E5EA)

/**
 * iOS 26 톤 alert 다이얼로그 (Figma "Alert- 기본1" 참고).
 *
 * - 흰 카드 + 20dp radius · 20dp padding
 * - 제목 (Semibold 17) + 본문 (Regular 14) 좌측 정렬
 * - 하단 capsule 버튼 스트립: 액션 2개면 가로 (오른쪽 끝 정렬), 3개+ 는 세로 스택
 * - 스크림 탭 / 시스템 백 = [onDismissRequest]
 *
 * @param title 다이얼로그 상단 굵은 제목.
 * @param message 부가 설명 (선택).
 * @param actions 표시할 버튼들. 좌측이 secondary, 우측이 primary 인 iOS 관습을 지킬 것.
 */
@Composable
fun AppAlertDialog(
    title: String,
    message: String? = null,
    actions: List<AlertAction>,
    onDismissRequest: () -> Unit,
) {
    // Dialog 는 안드로이드 Window/DecorView 로 렌더 → Compose Preview 의 layoutlib 이
    // 렌더 못 함. 그래서 실제 카드 UI 는 [AppAlertDialogContent] 로 분리해서 프리뷰 대상은
    // 카드 body 만 뽑아 그린다.
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppAlertDialogContent(title = title, message = message, actions = actions)
    }
}

/**
 * Dialog 안에 그릴 실제 alert 카드 본체. Dialog 프리뷰 제한 우회용으로 분리.
 * 실제 앱에선 [AppAlertDialog] 를 통해서만 사용.
 */
@Composable
internal fun AppAlertDialogContent(
    title: String,
    message: String? = null,
    actions: List<AlertAction>,
) {
    val font = LocalPretendardFontFamily.current
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = TextPrimary,
            ),
        )
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = TextStyle(
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = TextSecondary,
                ),
            )
        }
        Spacer(Modifier.height(20.dp))
        if (actions.size <= 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                actions.forEach { CapsuleActionButton(it) }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { CapsuleActionButton(it, fullWidth = true) }
            }
        }
    }
}

@Composable
private fun CapsuleActionButton(action: AlertAction, fullWidth: Boolean = false) {
    val font = LocalPretendardFontFamily.current
    val (bg, fg) = when (action.style) {
        AlertActionStyle.Default -> PrimaryBlue to Color.White
        AlertActionStyle.Cancel -> IosSecondaryBg to TextPrimary
        AlertActionStyle.Destructive -> IosRed to Color.White
    }
    Box(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.width(96.dp))
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = action.label,
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = fg,
            ),
        )
    }
}

// Dialog 프리뷰 우회: 스크림 대신 어두운 배경 Box 위에 카드 body 만 그려 실기기 렌더에 근사.
@Preview
@Composable
private fun AppAlertDialogPreview_TwoActions() {
    LinkerTheme {
        PreviewScrim {
            AppAlertDialogContent(
                title = "커플 연결 해제",
                message = "연결을 해제하면 파트너 스케줄은 더 이상 보이지 않습니다. 내가 만든 스케줄은 유지돼요.",
                actions = listOf(
                    AlertAction("취소", AlertActionStyle.Cancel) {},
                    AlertAction("해제", AlertActionStyle.Destructive) {},
                ),
            )
        }
    }
}

@Preview
@Composable
private fun AppAlertDialogPreview_Single() {
    LinkerTheme {
        PreviewScrim {
            AppAlertDialogContent(
                title = "저장되었습니다",
                actions = listOf(
                    AlertAction("확인", AlertActionStyle.Default) {},
                ),
            )
        }
    }
}

@Preview
@Composable
private fun AppAlertDialogPreview_ThreeStacked() {
    LinkerTheme {
        PreviewScrim {
            AppAlertDialogContent(
                title = "어떻게 저장할까요?",
                message = "3개 이상 액션이면 세로 스택으로 뜹니다.",
                actions = listOf(
                    AlertAction("이 일정만", AlertActionStyle.Default) {},
                    AlertAction("이후 모두", AlertActionStyle.Default) {},
                    AlertAction("취소", AlertActionStyle.Cancel) {},
                ),
            )
        }
    }
}

@Composable
private fun PreviewScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x80000000))
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}