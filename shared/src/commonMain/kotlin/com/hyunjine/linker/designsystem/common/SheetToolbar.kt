package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.OnPrimary
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.TextPrimary

/**
 * 편집용 바텀 시트 상단 툴바. 좌측 ✕ 원형 dismiss + 중앙 타이틀 + 우측 리퀴드 글래스 "저장" pill.
 * 일정 추가 화면 ([SaveActionPill] · [BackCircleButton]) 과 시각·톤을 통일한다 (#247).
 *
 * @param title 중앙 타이틀 텍스트.
 * @param onCancel ✕ 탭 콜백 — 시트 닫기.
 * @param onConfirm 저장 pill 탭 콜백. [confirmEnabled] false 면 무시.
 * @param confirmEnabled 저장 활성화 여부. false 면 pill 이 반투명.
 * @param confirmLabel 저장 버튼 텍스트 (기본 "저장" · 상황에 따라 "확인" · "완료" 등).
 * @param modifier 외부 [Modifier].
 */
@Composable
fun SheetToolbar(
    title: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmLabel: String = "저장",
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        CircleCloseButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
        SaveActionPill(
            label = confirmLabel,
            enabled = confirmEnabled,
            onClick = onConfirm,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * ✕ 원형 dismiss 버튼. iOS 26 sheet 상단 우측 close 스타일 근사 — 44dp 원 · SurfaceCard fill · TextPrimary 심볼.
 */
@Composable
fun CircleCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(SurfaceCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✕",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
            ),
        )
    }
}

/**
 * iOS 26 리퀴드 글래스 primary pill. 중앙에 [label] 텍스트, disabled 시 알파 감쇠.
 * `CreateScheduleScreen.SaveAction` 과 시각 동일 — 상단바 · 시트 양쪽에서 공용.
 */
@Composable
fun SaveActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .liquidGlass(shape = CircleShape, fill = SolidColor(PrimaryBlue))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = OnPrimary,
            ),
        )
    }
}

// ---------- Previews ----------

@Preview
@Composable
private fun SheetToolbarPreview_Enabled() {
    ProvidePretendard {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F7))
                .padding(16.dp),
        ) {
            SheetToolbar(
                title = "커스텀 색상",
                onCancel = {},
                onConfirm = {},
                confirmEnabled = true,
            )
        }
    }
}

@Preview
@Composable
private fun SheetToolbarPreview_Disabled() {
    ProvidePretendard {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F7))
                .padding(16.dp),
        ) {
            SheetToolbar(
                title = "닉네임",
                onCancel = {},
                onConfirm = {},
                confirmEnabled = false,
                confirmLabel = "완료",
            )
        }
    }
}
