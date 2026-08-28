package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary

/**
 * 프로필 · 커플 · 스케줄 등에서 공용으로 쓰는 라벨 + 텍스트필드 카드.
 * Figma iOS 26 스타일 — 흰 카드 위에 좌측 100dp 라벨 + 우측 입력. 17sp · PrimaryBlue 커서.
 *
 * @param label 좌측에 고정 폭으로 표시할 라벨 (예: "닉네임", "초대코드").
 * @param value 현재 입력 값.
 * @param onValueChange 입력 변경 콜백.
 * @param placeholder value 가 비어있을 때 회색으로 표시할 힌트. null 이면 표시 안 함.
 * @param capitalization 자동 대문자 정책 (초대코드 = Characters).
 * @param imeAction 키보드 우하단 액션 (기본 Done).
 * @param onImeAction Done/Search 등 IME 액션 콜백. null 이면 기본 keyboardActions.
 * @param focusRequester 외부에서 포커스 제어 시 전달.
 */
@Composable
fun AppInputCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    labelWidth: androidx.compose.ui.unit.Dp = 100.dp,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
        )
        Box(Modifier.fillMaxWidth()) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    style = TextStyle(color = TextSecondary, fontSize = 17.sp, fontFamily = font),
                )
            }
            val fieldModifier = if (focusRequester != null) {
                Modifier.fillMaxWidth().focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = fieldModifier,
                textStyle = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
                singleLine = true,
                cursorBrush = SolidColor(PrimaryBlue),
                keyboardOptions = KeyboardOptions(
                    capitalization = capitalization,
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction?.invoke() },
                    onSearch = { onImeAction?.invoke() },
                    onGo = { onImeAction?.invoke() },
                    onNext = { onImeAction?.invoke() },
                    onSend = { onImeAction?.invoke() },
                ),
            )
        }
    }
}
