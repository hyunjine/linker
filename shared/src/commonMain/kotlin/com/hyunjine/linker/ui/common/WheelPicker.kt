package com.hyunjine.linker.ui.common

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.platform.rememberSelectionHaptic
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.TextPrimary
import kotlin.math.abs
import kotlinx.coroutines.flow.drop

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
    itemHeight: Dp = 40.dp,
    textAlign: TextAlign = TextAlign.Center,
    fontSize: TextUnit = 18.sp,
) {
    require(visibleItemCount % 2 == 1) { "visibleItemCount must be odd" }
    val halfCount = visibleItemCount / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(listState)
    val font = LocalPretendardFontFamily.current
    val fireHaptic = rememberSelectionHaptic()

    // 스크롤이 멈춘 뒤 firstVisibleItemIndex 가 곧 선택된 인덱스.
    // scrollInProgress 가 false 로 떨어질 때만 방출해 스크롤 중 중복 콜백 방지.
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .collect { (scrolling, idx) ->
                if (!scrolling && idx != selectedIndex && idx in items.indices) {
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
