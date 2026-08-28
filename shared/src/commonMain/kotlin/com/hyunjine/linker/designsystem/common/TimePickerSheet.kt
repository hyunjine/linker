package com.hyunjine.linker.designsystem.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.designsystem.theme.ProvidePretendard
import com.hyunjine.linker.designsystem.theme.SurfaceCard

/**
 * iOS 스타일 시각 피커 바텀시트 (Figma 2818:59417).
 *
 * 세 컬럼 [WheelPicker]: `오전/오후` · `1~12시` · `00~55분(5분 단위)`. 스타일은 년/월/일 피커와
 * 동일하게 [PickerSurface] + 하단 [ConfirmCta] 를 재사용한다.
 *
 * [time] 은 24h `"HH:MM"` 문자열이며, 5분 단위가 아닌 분은 가장 가까운 5분 슬롯으로 스냅해서
 * 초기 draft 를 만든다. `null`/파싱 실패 시 `10:00` 을 기본값으로 사용.
 *
 * [minTime]/[maxTime] 을 지정하면 그 범위 밖 시각은 wheel 목록에서 아예 사라져 선택이 불가능해진다
 * (YearMonthDayPickerSheet 와 동일 패턴). 현재 선택된 오전/오후·시에 따라 하위 컬럼이 동적으로
 * 잘려서 12h 표기의 경계 케이스에도 유효 조합만 남는다.
 *
 * dismiss 의미는 [YearMonthPickerSheet] 와 동일 — CTA=확정, 그 외=취소. [onConfirm] 은
 * 확정 시각을 24h `"HH:MM"` 문자열로 전달.
 */
@Composable
fun TimePickerSheet(
    visible: Boolean,
    time: String?,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    minTime: String? = null,
    maxTime: String? = null,
) {
    // min 은 위로 반올림, max 는 아래로 반올림해 5분 스텝 안에서 restrictive 하게 잡는다.
    val minM = remember(minTime) { minTime?.let { parseHhMm(it) }?.let { roundUpTo5(it) } ?: 0 }
    val maxM = remember(maxTime) { maxTime?.let { parseHhMm(it) }?.let { roundDownTo5(it) } ?: MaxMinutes }
    val lo = minOf(minM, maxM)
    val hi = maxOf(minM, maxM)

    val initial = remember(visible, time, lo, hi) {
        toDraft((parseHhMm(time)?.let { snapTo5(it) } ?: DefaultMinutes).coerceIn(lo, hi))
    }
    var draftAmPm by remember(visible) { mutableStateOf(initial.ampm) }
    var draftHour by remember(visible) { mutableStateOf(initial.hour) }
    var draftMinute by remember(visible) { mutableStateOf(initial.minute) }

    // 오전(0) / 오후(1) 중 실제로 유효 시각이 있는 값만.
    val ampmValues = remember(lo, hi) {
        buildList {
            if (lo < NoonMinutes) add(0)
            if (hi >= NoonMinutes) add(1)
        }
    }
    if (draftAmPm !in ampmValues) draftAmPm = ampmValues.first()

    // 현재 ampm 에서 유효한 12h 시(1..12) 값.
    val hourValues = remember(draftAmPm, lo, hi) {
        (1..12).filter { h -> hasValidMinuteSlot(to24h(draftAmPm, h), lo, hi) }
    }
    if (draftHour !in hourValues) draftHour = hourValues.first()

    // 현재 ampm + hour 에서 유효한 분(0,5,...,55) 값.
    val minuteValues = remember(draftAmPm, draftHour, lo, hi) {
        val h24 = to24h(draftAmPm, draftHour)
        (0..55 step 5).filter { m -> (h24 * 60 + m) in lo..hi }
    }
    if (draftMinute !in minuteValues) draftMinute = minuteValues.first()

    val ampmItems = ampmValues.map { if (it == 0) "오전" else "오후" }
    val hourItems = hourValues.map { it.toString() }
    val minuteItems = minuteValues.map { it.toString().padStart(2, '0') }

    AppBottomSheet(
        visible = visible,
        onDismissRequest = onCancel,
    ) {
        PickerSurface {
            WheelPicker(
                items = ampmItems,
                selectedIndex = ampmValues.indexOf(draftAmPm).coerceAtLeast(0),
                onSelectedChange = { draftAmPm = ampmValues[it] },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = hourItems,
                selectedIndex = hourValues.indexOf(draftHour).coerceAtLeast(0),
                onSelectedChange = { draftHour = hourValues[it] },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = minuteItems,
                selectedIndex = minuteValues.indexOf(draftMinute).coerceAtLeast(0),
                onSelectedChange = { draftMinute = minuteValues[it] },
                modifier = Modifier.weight(1f),
            )
        }
        ConfirmCta {
            val total = to24h(draftAmPm, draftHour) * 60 + draftMinute
            onConfirm(formatMinutes(total))
        }
    }
}

private data class TimeDraft(val ampm: Int, val hour: Int, val minute: Int)

/** 하루 minutes-of-day 값을 `(ampm, hour12, minute5)` 트리플로 분해. */
private fun toDraft(minutesOfDay: Int): TimeDraft {
    val h24 = minutesOfDay / 60
    val m = (minutesOfDay % 60 / 5) * 5
    val ampm = if (h24 < 12) 0 else 1
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return TimeDraft(ampm, h12, m)
}

/** 오전/오후 + 12h 시 → 24h 시. 오전 12 = 0시, 오후 12 = 12시. */
private fun to24h(ampm: Int, hour12: Int): Int = when {
    ampm == 0 && hour12 == 12 -> 0
    ampm == 0 -> hour12
    ampm == 1 && hour12 == 12 -> 12
    else -> hour12 + 12
}

/** 주어진 24h 시(hour24) 안에 [lo]..[hi] 와 겹치는 5분 슬롯이 하나라도 있는지. */
private fun hasValidMinuteSlot(hour24: Int, lo: Int, hi: Int): Boolean =
    (0..55 step 5).any { m -> (hour24 * 60 + m) in lo..hi }

/** `"HH:MM"` 24h 문자열 → 하루 minutes. 파싱 실패 시 `null`. */
private fun parseHhMm(s: String?): Int? {
    if (s.isNullOrBlank()) return null
    val p = s.split(':')
    if (p.size != 2) return null
    val h = p[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val m = p[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return h * 60 + m
}

/** 5분 스텝에 가장 가까운 슬롯으로 반올림. 상한 [MaxMinutes] 로 클램프. */
private fun snapTo5(minutes: Int): Int = (((minutes + 2) / 5) * 5).coerceIn(0, MaxMinutes)

/** min 경계용 — 5분 단위 위쪽으로 반올림 (더 restrictive). */
private fun roundUpTo5(minutes: Int): Int = (((minutes + 4) / 5) * 5).coerceIn(0, MaxMinutes)

/** max 경계용 — 5분 단위 아래쪽으로 반올림 (더 restrictive). */
private fun roundDownTo5(minutes: Int): Int = ((minutes / 5) * 5).coerceIn(0, MaxMinutes)

private fun formatMinutes(total: Int): String {
    val h = total / 60
    val m = total % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

private const val NoonMinutes = 12 * 60
private const val MaxMinutes = 23 * 60 + 55
private const val DefaultMinutes = 10 * 60 // 오전 10:00

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
                .background(Color(0xFFB8B8B8)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SurfaceCard),
            ) {
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
private fun TimePickerSheetPreview() {
    val ampmItems = listOf("오전", "오후")
    val hourItems = (1..12).map { it.toString() }
    val minuteItems = (0..55 step 5).map { it.toString().padStart(2, '0') }
    var ampm by remember { mutableStateOf(0) }
    var hour by remember { mutableStateOf(7) } // 8시
    var minute by remember { mutableStateOf(6) } // 30분
    PickerSheetPreviewFrame {
        PickerSurface {
            WheelPicker(
                items = ampmItems,
                selectedIndex = ampm,
                onSelectedChange = { ampm = it },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = hourItems,
                selectedIndex = hour,
                onSelectedChange = { hour = it },
                modifier = Modifier.weight(1f),
            )
            WheelPicker(
                items = minuteItems,
                selectedIndex = minute,
                onSelectedChange = { minute = it },
                modifier = Modifier.weight(1f),
            )
        }
        ConfirmCta { }
    }
}
