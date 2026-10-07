package com.hyunjine.linker.feature.more

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppBottomSheet
import com.hyunjine.linker.designsystem.common.AppInputCard
import com.hyunjine.linker.designsystem.common.SheetToolbar
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextSecondary

/**
 * 팀 캘린더에 쓰는 내 이름을 입력받는 시트 (#242). `재택(양현진) 09:00-18:00` 의 괄호 안 이름과
 * 같아야 내 재택으로 가져온다. 빈 값은 저장할 수 없다.
 *
 * @param visible 시트 노출 여부.
 * @param initial 열릴 때 표시할 값 (저장된 이름 또는 빈 문자열).
 * @param onDismiss 취소 · 바깥 탭.
 * @param onConfirm 저장. trim 된 이름 전달.
 */
@Composable
fun OutlookNameSheet(
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
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val trimmed = value.trim()
    val canConfirm = trimmed.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetToolbar(
            title = "팀 캘린더 이름",
            onCancel = onCancel,
            onConfirm = { onConfirm(trimmed) },
            confirmEnabled = canConfirm,
        )
        AppInputCard(
            label = "이름",
            value = value,
            onValueChange = { value = it },
            placeholder = "양현진",
            focusRequester = focusRequester,
            onImeAction = { if (canConfirm) onConfirm(trimmed) },
            initialCursorAtEnd = true,
        )
        Text(
            text = "TeamAIVN 캘린더의 \"재택(이름) 09:00-18:00\" 에서 괄호 안 이름과 똑같이 입력해 주세요.",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontFamily = LocalPretendardFontFamily.current),
        )
    }
}
