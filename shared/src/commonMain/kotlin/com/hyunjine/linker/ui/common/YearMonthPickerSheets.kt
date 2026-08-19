package com.hyunjine.linker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.ui.theme.ProvidePretendard
import com.hyunjine.linker.ui.theme.SegmentTrack
import com.hyunjine.linker.ui.theme.SurfaceCard

/**
 * iOS 스타일 년/월 피커 바텀시트. 두 컬럼 [WheelPicker] + 중앙 SelectionBar + 상하 fade + 하단 CTA.
 *
 * dismiss 의미가 두 갈래로 나뉜다:
 *  - **CTA 탭**: 현재 스크롤된 값이 확정되어 [onConfirm] 호출.
 *  - **드래그 다운 · 스크림 탭**: 값 없이 [onCancel] 만 호출 (사용자가 결정을 취소).
 *
 * 두 경로 모두 상위에서 [visible] 을 `false` 로 바꿔야 시트가 실제로 닫힌다.
 */
@Composable
fun YearMonthPickerSheet(
    visible: Boolean,
    year: Int,
    month: Int,
    minYear: Int,
    maxYear: Int,
    onConfirm: (year: Int, month: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var draftYear by remember(visible) { mutableStateOf(year) }
    var draftMonth by remember(visible) { mutableStateOf(month) }

    val years = remember(minYear, maxYear) { (minYear..maxYear).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }

    AppBottomSheet(
        visible = visible,
        onDismissRequest = onCancel,
    ) {
        PickerSurface {
            WheelPicker(
                items = years,
                selectedIndex = (draftYear - minYear).coerceIn(0, years.lastIndex),
                onSelectedChange = { draftYear = minYear + it },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = months,
                selectedIndex = (draftMonth - 1).coerceIn(0, 11),
                onSelectedChange = { draftMonth = it + 1 },
                modifier = Modifier.weight(1f),
            )
        }
        ConfirmCta { onConfirm(draftYear, draftMonth) }
    }
}

/**
 * iOS 스타일 년/월/일 피커 바텀시트. 세 컬럼 [WheelPicker] + 중앙 SelectionBar + 상하 fade + 하단 CTA.
 *
 * 월 변경 시 day 상한이 자동 조정 (2월 → 28/29일, 4/6/9/11 → 30일). draft 가 초과되면 마지막 유효
 * 일자로 클램프.
 *
 * dismiss 의미는 [YearMonthPickerSheet] 와 동일 — CTA=확정, 그 외=취소.
 */
@Composable
fun YearMonthDayPickerSheet(
    visible: Boolean,
    year: Int,
    month: Int,
    day: Int,
    minYear: Int,
    maxYear: Int,
    onConfirm: (year: Int, month: Int, day: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var draftYear by remember(visible) { mutableStateOf(year) }
    var draftMonth by remember(visible) { mutableStateOf(month) }
    var draftDay by remember(visible) { mutableStateOf(day) }

    val years = remember(minYear, maxYear) { (minYear..maxYear).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }
    val maxDay = remember(draftYear, draftMonth) { daysInMonth(draftYear, draftMonth) }
    val days = remember(maxDay) { (1..maxDay).map { "${it}일" } }
    if (draftDay > maxDay) draftDay = maxDay

    AppBottomSheet(
        visible = visible,
        onDismissRequest = onCancel,
    ) {
        PickerSurface {
            WheelPicker(
                items = years,
                selectedIndex = (draftYear - minYear).coerceIn(0, years.lastIndex),
                onSelectedChange = { draftYear = minYear + it },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = months,
                selectedIndex = (draftMonth - 1).coerceIn(0, 11),
                onSelectedChange = { draftMonth = it + 1 },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = days,
                selectedIndex = (draftDay - 1).coerceIn(0, days.lastIndex),
                onSelectedChange = { draftDay = it + 1 },
                modifier = Modifier.weight(1f),
            )
        }
        ConfirmCta { onConfirm(draftYear, draftMonth, draftDay) }
    }
}

/**
 * 공통 피커 서피스. SelectionBar (중앙 44dp rounded rect) + [WheelPicker] 컬럼들 [content] +
 * 상하단 white fade 그라디언트를 스택. [content] 안 각 WheelPicker 는 `Modifier.weight(1f)` 로 균등 분배.
 */
@Composable
private fun PickerSurface(
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ItemHeight * VisibleItemCount),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(ItemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(SegmentTrack),
        )
        // Figma 2725:77274 기준 년 텍스트 x=130, 월 텍스트 x=272 (sheet 402dp) →
        // 컬럼 폭 141dp, 좌우 60dp 인셋. 이 인셋으로 columns 를 안쪽으로 모아 텍스트 간 여백 축소.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 60.dp),
            content = content,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(FadeHeight)
                .background(Brush.verticalGradient(listOf(SurfaceCard, Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(FadeHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, SurfaceCard))),
        )
    }
}

/** 하단 CTA — 좌우 16dp 여백, 시트 하단 24dp 여백. 프로젝트 표준 [PrimaryButton] 사용. */
@Composable
private fun ColumnScope.ConfirmCta(onClick: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        PrimaryButton(text = "완료", onClick = onClick)
    }
    Spacer(Modifier.height(24.dp))
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

/** WheelPicker 기본값과 동일. SelectionBar 높이도 이 값을 재사용한다. */
private val ItemHeight = 44.dp
private const val VisibleItemCount = 5
private val FadeHeight = 60.dp

// ---------- Previews ----------
// AppBottomSheet 은 내부적으로 Dialog 를 사용해 layoutlib 프리뷰가 제한적이라
// (프로젝트 규약: AppBottomSheet.kt 참고) 실제 sheet 대신 sheet 내부에 들어갈 body 만
// 카드로 감싸 그려서 상태별 콘텐츠 확인용으로 사용한다.

@Composable
private fun PickerSheetPreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    ProvidePretendard {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFB8B8B8)), // dim scrim 근사
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SurfaceCard),
            ) {
                // 드래그 핸들 자리
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFFD1D1D6)),
                )
                content()
            }
        }
    }
}

@Preview
@Composable
private fun YearMonthPickerSheetPreview() {
    var draftYear by remember { mutableStateOf(1998) }
    var draftMonth by remember { mutableStateOf(5) }
    val years = remember { (1996..2000).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }
    PickerSheetPreviewFrame {
        PickerSurface {
            WheelPicker(
                items = years,
                selectedIndex = draftYear - 1996,
                onSelectedChange = { draftYear = 1996 + it },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = months,
                selectedIndex = draftMonth - 1,
                onSelectedChange = { draftMonth = it + 1 },
                modifier = Modifier.weight(1f),
            )
        }
        ConfirmCta { }
    }
}

@Preview
@Composable
private fun YearMonthDayPickerSheetPreview() {
    var draftYear by remember { mutableStateOf(1998) }
    var draftMonth by remember { mutableStateOf(5) }
    var draftDay by remember { mutableStateOf(24) }
    val years = remember { (1996..2000).map { "${it}년" } }
    val months = remember { (1..12).map { "${it}월" } }
    val maxDay = remember(draftYear, draftMonth) {
        when (draftMonth) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((draftYear % 4 == 0 && draftYear % 100 != 0) || draftYear % 400 == 0) 29 else 28
            else -> 30
        }
    }
    val days = remember(maxDay) { (1..maxDay).map { "${it}일" } }
    PickerSheetPreviewFrame {
        PickerSurface {
            WheelPicker(
                items = years,
                selectedIndex = draftYear - 1996,
                onSelectedChange = { draftYear = 1996 + it },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = months,
                selectedIndex = draftMonth - 1,
                onSelectedChange = { draftMonth = it + 1 },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = days,
                selectedIndex = (draftDay - 1).coerceIn(0, days.lastIndex),
                onSelectedChange = { draftDay = it + 1 },
                modifier = Modifier.weight(1f),
            )
        }
        ConfirmCta { }
    }
}
