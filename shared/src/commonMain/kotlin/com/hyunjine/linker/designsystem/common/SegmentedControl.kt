package com.hyunjine.linker.designsystem.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SegmentTrack
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary

// Figma 2772:78828 스펙: 트랙 370×32, 상하좌우 2dp 인셋, 버튼 자체 높이 28dp.
// 트랙/pill 모두 완전한 pill(capsule) 형태 — `CircleShape` (radius=50%) 사용.
private val SegmentItemHeight = 28.dp
private val SegmentTrackInset = 2.dp

/**
 * iOS 스타일 세그먼트 컨트롤. 트랙 배경 (SegmentTrack) 위에 선택된 슬롯을 흰색 pill 로 강조하며,
 * 다른 항목을 선택하면 pill 이 좌↔우로 슬라이드 애니메이션되며 이동한다.
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
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(SegmentTrack)
            .padding(SegmentTrackInset),
    ) {
        // 트랙 안쪽(padding 제외 후) 폭을 항목 수로 균등 분할.
        val itemWidth = maxWidth / options.size
        val animatedOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "segmentOffset",
        )

        // 하이라이트 pill — 트랙 위, 텍스트 아래 레이어. Row 보다 먼저 그려져야 텍스트가 pill 위에 올라감.
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(itemWidth)
                .height(SegmentItemHeight)
                .clip(CircleShape)
                .background(SurfaceCard),
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentItem(
                    text = label(option),
                    selected = index == selectedIndex,
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .weight(1f)
                        .height(SegmentItemHeight),
                )
            }
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
    // iOS 스타일: Material 리플 대신 press 시 살짝 흐려지는 opacity 피드백. 배경 pill 은 상위 오버레이에서
    // 그려주므로 여기서는 배경을 두지 않는다 (이중으로 그리면 애니메이션 중 잔상 발생).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.6f else 1f, label = "segmentPress")
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .alpha(pressAlpha),
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
