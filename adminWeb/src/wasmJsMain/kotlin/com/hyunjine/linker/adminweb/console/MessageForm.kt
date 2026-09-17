package com.hyunjine.linker.adminweb.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.adminweb.ui.Colors
import com.hyunjine.linker.adminweb.ui.SendActionPill

/**
 * 우측 메시지 입력 폼 — 제목 · 내용 · 요약 · 발송 버튼.
 *
 * 상태 · 액션은 모두 [ConsoleState] 가 소유. 이 컴포저블은 렌더링과 폼 이벤트 위임만 담당.
 *
 * @param state 콘솔 화면 전체 상태 홀더.
 * @param modifier 패널을 감싸는 외부 modifier — 상위에서 사이즈/위치 배정.
 */
@Composable
fun MessageForm(
    state: ConsoleState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Colors.Surface)
            .padding(24.dp),
    ) {
        FieldLabel(text = "제목")
        Spacer(Modifier.height(8.dp))
        TitleField(
            value = state.title,
            onValueChange = { newValue ->
                // 하드 캡. 초과 붙여넣기 시도도 조용히 잘라내 요약 카운트가 흐트러지지 않도록.
                state.title = newValue.take(ConsoleState.TitleMaxLength)
            },
        )
        Spacer(Modifier.height(20.dp))

        FieldLabel(text = "내용")
        Spacer(Modifier.height(8.dp))
        BodyField(
            value = state.body,
            onValueChange = { newValue ->
                state.body = newValue.take(ConsoleState.BodyMaxLength)
            },
        )

        Spacer(Modifier.height(16.dp))
        SummaryRow(state = state)

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            SendActionPill(
                label = "발송",
                enabled = state.canSend,
                onClick = { state.send() },
                loading = state.sending,
            )
        }
    }
}

/** 필드 라벨 — 인풋 상단에 놓이는 짧은 텍스트. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Colors.TextSecondary,
    )
}

/** 46 tall · radius 12 · 회색 배경 한 줄 인풋. */
@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Colors.FieldFill)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = "발송할 제목을 입력하세요",
                color = Colors.TextTertiary,
                fontSize = 15.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                color = Colors.TextPrimary,
                fontSize = 15.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 여러 줄 · 320 tall · radius 12 본문 인풋. */
@Composable
private fun BodyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Colors.FieldFill)
            .padding(14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "본문 내용을 입력하세요",
                color = Colors.TextTertiary,
                fontSize = 15.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(
                color = Colors.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * "선택 N명 · 제목 A자 · 내용 B자" 요약. 초과 시 해당 카운트를 빨강으로.
 *
 * — docs §3.4 · §7.
 */
@Composable
private fun SummaryRow(state: ConsoleState) {
    val selectedCount = state.selectedIds.size
    val titleLength = state.title.length
    val bodyLength = state.body.length
    val titleColor = if (titleLength > ConsoleState.TitleMaxLength) Colors.Danger else Colors.TextSecondary
    val bodyColor = if (bodyLength > ConsoleState.BodyMaxLength) Colors.Danger else Colors.TextSecondary

    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = Colors.TextSecondary)) {
            append("선택 ${selectedCount}명 · 제목 ")
        }
        withStyle(SpanStyle(color = titleColor, fontWeight = FontWeight.SemiBold)) {
            append("${titleLength}자")
        }
        withStyle(SpanStyle(color = Colors.TextSecondary)) {
            append(" · 내용 ")
        }
        withStyle(SpanStyle(color = bodyColor, fontWeight = FontWeight.SemiBold)) {
            append("${bodyLength}자")
        }
    }
    Text(
        text = annotated,
        style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}
