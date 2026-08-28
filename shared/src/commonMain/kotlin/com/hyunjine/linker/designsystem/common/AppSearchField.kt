package com.hyunjine.linker.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_clear_circle
import org.jetbrains.compose.resources.painterResource

/**
 * 검색 상단바 등에서 쓰는 40dp 텍스트필드. 좌측에 값 입력, 값이 있으면 우측에 clear (원형 X) 노출.
 * clear 는 박스 **안** 에 있어야 함 — 밖에 두면 IME 활성 상태에서 정렬이 흔들림.
 *
 * @param value 현재 입력 값.
 * @param onValueChange 입력 변경 콜백.
 * @param onClear 값 지우기 (clear 아이콘 탭).
 * @param placeholder 값이 비었을 때 회색 힌트.
 * @param imeAction 기본 Search.
 * @param onImeAction 키보드 액션 콜백 (엔터 등).
 * @param autoFocus true 면 mount 시 자동 포커스.
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "제목 검색",
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: (() -> Unit)? = null,
    autoFocus: Boolean = true,
) {
    val font = LocalPretendardFontFamily.current
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontFamily = font,
                    ),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(TextPrimary),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = font,
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = { onImeAction?.invoke() },
                    onDone = { onImeAction?.invoke() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_clear_circle),
                    contentDescription = "지우기",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
