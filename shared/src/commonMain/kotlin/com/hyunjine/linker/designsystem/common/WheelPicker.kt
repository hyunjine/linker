package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.platform.rememberSelectionHaptic
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.TextPrimary
import kotlin.math.abs
import kotlinx.coroutines.flow.drop

/**
 * flick velocity 를 이 배율로 감쇠시켜 fling 관성을 잘라낸다. 0f 면 관성 완전 제거
 * (플릭해도 손 뗀 순간 바로 정지 후 snap), 1f 면 Compose 기본 (관성 길게 남음).
 * iOS UIPickerView 대비 빠릿한 응답성을 목표로 0.15 f 채택 — flick 이 "있는 듯 마는 듯".
 */
private const val FLING_VELOCITY_SCALE = 0.15f

/**
 * iOS UIPickerView 스타일의 세로 스크롤 휠 피커.
 *
 * `LazyColumn` + `rememberSnapFlingBehavior` 조합으로 항목이 한 칸씩 스냅되며,
 * 중앙에서 멀어질수록 텍스트 알파/두께가 자동으로 흐릿해진다.
 * 중앙 하이라이트 배경은 이 컴포저블 밖에서 오버레이해야 한다 — 여러 피커를
 * 나란히 두고 하나의 하이라이트 바로 관통시키는 케이스를 지원하기 위함.
 *
 * 스크롤이 한 칸 이동할 때마다 [HapticFeedbackType.TextHandleMove] 햅틱이 울린다.
 *
 * @param items 표시할 문자열 목록. 순서 그대로 위에서 아래로 렌더링된다.
 * @param selectedIndex 현재 선택된 인덱스(중앙에 오는 아이템). 외부에서 프로그램적으로
 * 바꾸면 자동으로 해당 아이템으로 스크롤된다.
 * @param onSelectedChange 스크롤이 멈추고 새로운 인덱스로 스냅됐을 때 호출.
 * 스크롤 진행 중에는 발화하지 않아 중복 콜백을 방지한다.
 * @param modifier 컨테이너에 적용할 [Modifier].
 * @param visibleItemCount 화면에 보이는 아이템 개수. 중앙 정렬을 위해 홀수여야 한다.
 * @param itemHeight 개별 아이템의 높이. 전체 컴포저블 높이는 `itemHeight × visibleItemCount`.
 * @param textAlign 아이템 텍스트 정렬. 기본은 가운데.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,
    itemHeight: Dp = 44.dp,
    textAlign: TextAlign = TextAlign.Center,
    fontSize: TextUnit = 22.sp,
) {
    require(visibleItemCount % 2 == 1) { "visibleItemCount must be odd" }
    val halfCount = visibleItemCount / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    // 기본 `rememberSnapFlingBehavior` 는 iOS UIPickerView 대비 관성이 길게 남아
    // "휘리릭" 감이 강하다. flick velocity 를 크게 잘라 관성을 최소화하고 snap 이
    // 즉시 붙도록 감싼다 — flick 이 거의 없는 것처럼 빠릿한 응답성.
    val baseFling = rememberSnapFlingBehavior(listState)
    val fling = remember(baseFling) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float =
                with(baseFling) { performFling(initialVelocity * FLING_VELOCITY_SCALE) }
        }
    }
    val font = LocalPretendardFontFamily.current
    val fireHaptic = rememberSelectionHaptic()

    // 스크롤 중에도 중앙 아이템 인덱스를 상위로 즉시 방출한다. 이렇게 해두면
    // 사용자가 스크롤 중 상단 "완료" 를 눌러 시트를 닫아도 그 시점에 화면 중앙에
    // 있던 값이 부모 state 에 이미 반영돼 최근접 항목이 그대로 확정된다.
    // 스냅 애니메이션이 짧아 (fling 감쇠 0.15f) 과도한 중복 콜백 우려는 낮음.
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                if (idx != selectedIndex && idx in items.indices) {
                    onSelectedChange(idx)
                }
            }
    }

    // 외부에서 selectedIndex 가 프로그램적으로 바뀌면 스크롤 위치를 맞춰준다.
    LaunchedEffect(selectedIndex) {
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    // firstVisibleItemIndex 는 contentPadding 덕분에 곧 화면 중앙 아이템 인덱스와 같다.
    val centeredIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 한 칸 이동할 때마다 짧은 selection 햅틱. drop(1) 로 초기 컴포지션 fire 는 스킵.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .collect { fireHaptic() }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItemCount),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * halfCount),
        ) {
            itemsIndexed(items) { index, text ->
                val distance = abs(index - centeredIndex)
                // 중앙에서 멀어질수록 흐릿하게. Figma 톤 근사치.
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.35f
                    else -> 0.18f
                }
                val weight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        textAlign = textAlign,
                        style = TextStyle(
                            color = TextPrimary.copy(alpha = alpha),
                            fontSize = fontSize,
                            fontWeight = weight,
                            fontFamily = font,
                        ),
                    )
                }
            }
        }
    }
}

// ---------- Previews ----------

@Preview
@Composable
private fun WheelPickerPreview_Single() {
    val items = remember { (1..12).map { "${it}월" } }
    var idx by remember { mutableStateOf(4) }
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            WheelPicker(
                items = items,
                selectedIndex = idx,
                onSelectedChange = { idx = it },
            )
        }
    }
}

@Preview
@Composable
private fun WheelPickerPreview_YearMonth() {
    val years = remember { (1996..2000).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }
    var yi by remember { mutableStateOf(2) }
    var mi by remember { mutableStateOf(4) }
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                WheelPicker(
                    items = years,
                    selectedIndex = yi,
                    onSelectedChange = { yi = it },
                    modifier = Modifier.weight(1f),
                )
                WheelPicker(
                    items = months,
                    selectedIndex = mi,
                    onSelectedChange = { mi = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
