package com.hyunjine.linker.feature.everytime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppBottomSheet
import com.hyunjine.linker.designsystem.common.AppInputCard
import com.hyunjine.linker.designsystem.common.SheetToolbar
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextSecondary

/**
 * 본인 에브리타임 시간표 공유 URL 을 입력받는 시트 (#306).
 *
 * 저장 정책:
 * - 유효한 URL/identifier: 정상 저장.
 * - 빈 문자열: identifier 를 NULL 로 되돌려 시간표 노출 취소.
 * - 유효하지 않은 값: 저장 버튼 비활성.
 *
 * [onConfirm] 은 사용자가 붙여넣은 raw 문자열을 그대로 전달 — 파싱 · 저장은 상위(VM) 담당.
 *
 * @param visible 시트 노출 여부. false 이면 dismissed 상태.
 * @param initial 열릴 때 표시할 초기 값. 편집이면 기존 저장값, 최초 등록이면 빈 문자열.
 * @param onDismiss X 버튼 · 바깥 탭 · dismiss 시 호출.
 * @param onConfirm 저장 pill 또는 키보드 return 시 호출. trim 된 문자열 전달.
 */
@Composable
fun EverytimeUrlSheet(
    visible: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AppBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = SurfaceGray,
    ) {
        SheetBody(initial = initial, onCancel = onDismiss, onConfirm = onConfirm)
    }
}

@Composable
private fun SheetBody(
    initial: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = value.trim()
    val parsed = EverytimeUrl.parseIdentifier(trimmed)
    val isEmpty = trimmed.isEmpty()
    val canConfirm = isEmpty || parsed != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetToolbar(
            title = "에브리타임 URL",
            onCancel = onCancel,
            onConfirm = { onConfirm(trimmed) },
            confirmEnabled = canConfirm,
        )
        AppInputCard(
            label = "URL",
            value = value,
            onValueChange = { value = it },
            placeholder = "https://everytime.kr/@…",
            keyboardType = KeyboardType.Uri,
            focusRequester = focusRequester,
            onImeAction = { if (canConfirm) onConfirm(trimmed) },
            initialCursorAtEnd = true,
        )
        val hint = when {
            isEmpty -> "URL 을 지우면 시간표 공유가 취소돼요."
            parsed != null -> "확인됨: @$parsed"
            else -> "올바른 에브리타임 URL 이 아니에요. 예: https://everytime.kr/@abc123"
        }
        val hintColor = when {
            isEmpty -> TextSecondary
            parsed != null -> PrimaryBlue
            else -> Color(0xFFE53935)
        }
        Text(
            text = hint,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(color = hintColor, fontSize = 13.sp, fontFamily = font),
        )
    }
}
