package com.hyunjine.linker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.AppBottomSheet
import com.hyunjine.linker.ui.common.WheelPicker
import com.hyunjine.linker.ui.theme.SegmentTrack
import com.hyunjine.linker.ui.theme.SurfaceCard

/**
 * iOS 캘린더 스타일 년/월 피커 바텀시트. 상단 툴바의 "YYYY. M v" 탭 시 오픈.
 *
 * 사용자가 휠을 굴리는 동안은 draft 상태만 갱신하고, **시트가 닫힐 때**(드래그 다운·스크림 탭 등)
 * 그 시점의 draft 값을 [onDismiss] 로 확정 전달한다. 별도 "확인" 버튼 없음.
 *
 * 두 컬럼 WheelPicker + 중앙 SelectionBar (rounded rect) + 상하단 white fade 그라디언트로
 * iOS 26 톤을 근사.
 *
 * @param visible 시트 표시 여부.
 * @param year 최초 선택될 연도.
 * @param month 최초 선택될 월 (1~12).
 * @param minYear 년 휠의 최소값.
 * @param maxYear 년 휠의 최대값 (보통 오늘 기준 현재 연도).
 * @param onDismiss 시트가 닫힐 때, 마지막으로 스냅된 (year, month) 로 호출. 상위에서 [visible] 을
 *   `false` 로 바꿔야 시트가 실제로 사라진다.
 */
@Composable
fun YearMonthPickerSheet(
    visible: Boolean,
    year: Int,
    month: Int,
    minYear: Int,
    maxYear: Int,
    onDismiss: (year: Int, month: Int) -> Unit,
) {
    // draft 는 시트가 다시 열릴 때(visible=true 전환) 최신 year/month 로 리셋되어야 하고,
    // 열려있는 동안 외부에서 year/month 가 바뀌어도 사용자의 스크롤을 덮어쓰지 않아야 한다.
    // → visible 만 key 로 두면 재오픈 시 리셋되고, 열린 동안은 유지됨.
    var draftYear by remember(visible) { mutableStateOf(year) }
    var draftMonth by remember(visible) { mutableStateOf(month) }

    val years = remember(minYear, maxYear) { (minYear..maxYear).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }

    AppBottomSheet(
        visible = visible,
        onDismissRequest = { onDismiss(draftYear, draftMonth) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ItemHeight * VisibleItemCount),
        ) {
            // 중앙 하이라이트 바 (SelectionBar). 휠보다 뒤에 그려져야 하므로 먼저 배치.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(ItemHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SegmentTrack),
            )
            // 두 컬럼 휠
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                WheelPicker(
                    items = years,
                    selectedIndex = (draftYear - minYear).coerceIn(0, years.lastIndex),
                    onSelectedChange = { draftYear = minYear + it },
                    modifier = Modifier.weight(1f),
                    visibleItemCount = VisibleItemCount,
                    itemHeight = ItemHeight,
                    fontSize = FontSize,
                )
                WheelPicker(
                    items = months,
                    selectedIndex = (draftMonth - 1).coerceIn(0, 11),
                    onSelectedChange = { draftMonth = it + 1 },
                    modifier = Modifier.weight(1f),
                    visibleItemCount = VisibleItemCount,
                    itemHeight = ItemHeight,
                    fontSize = FontSize,
                )
            }
            // 상단 fade — 위쪽 두 줄 알파 감쇠를 시각적으로 마무리 (white → transparent)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(FadeHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(SurfaceCard, Color.Transparent),
                        ),
                    ),
            )
            // 하단 fade — 대칭 (transparent → white)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FadeHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, SurfaceCard),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val ItemHeight = 44.dp
private const val VisibleItemCount = 5
private val FontSize = 22.sp
private val FadeHeight = 60.dp
