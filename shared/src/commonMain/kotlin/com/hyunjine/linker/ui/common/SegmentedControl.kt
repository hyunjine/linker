package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SegmentTrack
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary

/**
 * iOS 스타일 세그먼트 컨트롤. 트랙 배경 (SegmentTrack) 위에 선택된 항목만 흰색 pill 로 강조.
 *
 * @param options 항목 목록. 순서대로 좌→우 배치. 균등 폭.
 * @param selected 현재 선택된 항목.
 * @param onSelect 항목 탭 시 호출.
 * @param label 항목 → 표시 문자열 매핑.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SegmentTrack)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { option ->
            SegmentItem(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pretendard = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) SurfaceCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp,
                color = TextPrimary,
            ),
        )
    }
}

// ---------- Previews ----------

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceGray)
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun SegmentedControlPreview_TwoItems() {
    var selected by remember { mutableStateOf("일정") }
    PreviewFrame {
        SegmentedControl(
            options = listOf("할 일", "일정"),
            selected = selected,
            onSelect = { selected = it },
            label = { it },
        )
    }
}

@Preview
@Composable
private fun SegmentedControlPreview_ThreeItems() {
    var selected by remember { mutableStateOf("나") }
    PreviewFrame {
        SegmentedControl(
            options = listOf("나", "상대방", "공동"),
            selected = selected,
            onSelect = { selected = it },
            label = { it },
        )
    }
}

@Preview
@Composable
private fun SegmentedControlPreview_Stack() {
    // 실제 사용처처럼 여러 세그먼트를 나열해 톤 확인.
    var typeSel by remember { mutableStateOf("일정") }
    var ownerSel by remember { mutableStateOf("나") }
    PreviewFrame {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SegmentedControl(
                options = listOf("할 일", "일정"),
                selected = typeSel,
                onSelect = { typeSel = it },
                label = { it },
            )
            SegmentedControl(
                options = listOf("나", "상대방", "공동"),
                selected = ownerSel,
                onSelect = { ownerSel = it },
                label = { it },
            )
        }
    }
}
