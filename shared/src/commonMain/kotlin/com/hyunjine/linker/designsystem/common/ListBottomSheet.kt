package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_check
import org.jetbrains.compose.resources.painterResource

/** 탭 후 [onSelect] 발화 전 체크 표시가 보이도록 두는 시간. iOS Reminders/Settings 감각. */
private const val SelectionRevealDelayMillis = 300L

/**
 * iOS 스타일 단일 선택 리스트 바텀시트. Figma 2795:79178 톤을 따르며 CTA 없이 탭으로 확정.
 *
 * 구성:
 *  - 상단 [AppBottomSheet] 드래그 핸들 (기본)
 *  - 좌우 14dp 여백의 리스트 컨테이너
 *  - 각 행: `padding 16dp 좌우, 14dp 상하`, 텍스트 17sp Regular, 선택된 행은 우측에 24dp 체크 아이콘
 *  - 행 사이 divider 없음 (Figma 참조 — 리스트가 그 자체로 시트를 채움)
 *
 * 탭 → 체크가 즉시 이동해 시각적 피드백을 준 뒤 ~220ms 지연 후 [onSelect] 발화한다. 지연 없이 바로
 * dismiss 시키면 사용자가 어떤 항목을 골랐는지 확인하지 못한 채 시트가 닫혀 iOS 톤과 이질감이 생김.
 * 지연 중 다른 항목 탭은 무시되어 double-select 를 방지한다. 드래그 다운·스크림 탭은 [onDismiss].
 *
 * @param visible 시트 표시 여부.
 * @param options 표시할 항목 리스트. 순서대로 위→아래.
 * @param selected 현재 선택된 항목. `null` 이면 아무것도 체크되지 않음.
 * @param onSelect 사용자가 행을 탭한 뒤 [SelectionRevealDelayMillis] 만큼 지연 후 호출. 상위에서 상태
 *  갱신 + 시트 닫기 처리.
 * @param onDismiss 드래그 다운·스크림 탭 등 사용자 취소.
 * @param label 항목 → 화면에 표시할 문자열 매핑.
 */
@Composable
fun <T> ListBottomSheet(
    visible: Boolean,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    // 사용자가 새로 탭한 항목. 지연 발화 사이 동안 UI 상 체크 위치를 이걸로 덮어씌워
    // 원본 [selected] 보다 우선 표시한다. sheet 가 열릴 때마다 초기화.
    var pending by remember(visible) { mutableStateOf<T?>(null) }
    val effectiveSelected: T? = pending ?: selected

    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        delay(SelectionRevealDelayMillis)
        onSelect(target)
    }

    AppBottomSheet(visible = visible, onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp),
        ) {
            options.forEach { option ->
                ListBottomSheetRow(
                    label = label(option),
                    selected = option == effectiveSelected,
                    // pending != null 인 동안 다른 항목 탭 무시 → 지연 중 중복 발화 방지.
                    onClick = { if (pending == null) pending = option },
                )
            }
        }
    }
}

@Composable
private fun ListBottomSheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                color = TextPrimary,
                lineHeight = 22.sp,
            ),
        )
        if (selected) {
            Image(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = "선택됨",
                colorFilter = ColorFilter.tint(TextPrimary),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ---------- Previews ----------
// AppBottomSheet 은 Dialog 기반이라 프리뷰 렌더가 제한적 → sheet body 만 카드로 감싸 프리뷰.

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceGray),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SurfaceCard)
                    .padding(vertical = 8.dp),
            ) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun ListBottomSheetPreview_Repeat() {
    val options = listOf("반복 안함", "매일", "매주", "매월", "매년", "사용자 설정")
    var selected by remember { mutableStateOf(options[0]) }
    PreviewFrame {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp),
        ) {
            options.forEach { opt ->
                ListBottomSheetRow(
                    label = opt,
                    selected = opt == selected,
                    onClick = { selected = opt },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ListBottomSheetPreview_MidSelection() {
    val options = listOf("반복 안함", "매일", "매주", "매월", "매년", "사용자 설정")
    var selected by remember { mutableStateOf(options[2]) }
    PreviewFrame {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp),
        ) {
            options.forEach { opt ->
                ListBottomSheetRow(
                    label = opt,
                    selected = opt == selected,
                    onClick = { selected = opt },
                )
            }
        }
    }
}
