package com.hyunjine.linker.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppBottomSheet
import com.hyunjine.linker.designsystem.common.ConfirmCta
import com.hyunjine.linker.designsystem.common.PickerSurface
import com.hyunjine.linker.designsystem.common.WheelPicker
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.SeparatorGrouped
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_check
import org.jetbrains.compose.resources.painterResource

/**
 * 일정 반복 규칙 선택 시트. 디자인: Figma "일정 반복 기능" 프레임 (3275:89908).
 *
 * 상단 nav bar (취소 · 반복) → 5개 반복 옵션 (안 함/매일/매주/매월/매년) → 선택된 옵션에 따른
 * sub-config (요일 chip · 날짜 grid · 월·일 grid) → 종료 날짜 wheel picker → 완료 CTA.
 *
 * 상단 그랩 핸들 + 드래그다운 · 스크림 탭으로 취소 (onDismiss). "완료" 탭 시에만 (rule, endDate)
 * 로 [onConfirm] 발화. 반복 없음을 확정할 때도 endDate 는 null 로 넘어간다.
 *
 * @param visible 시트 표시 여부.
 * @param current 현재 draft 의 반복 규칙.
 * @param currentEndDate 현재 draft 의 종료일 (없으면 anchor+1년 을 기본값으로 사용).
 * @param anchorDate 사용자가 처음 매주/매월/매년을 선택했을 때 기본값 산정 기준일 (일정 시작일).
 * @param onConfirm 사용자가 완료 CTA 를 눌렀을 때. endDate 는 rule=None 이면 null.
 * @param onDismiss 드래그 다운 · 스크림 탭 · 취소 버튼.
 */
@Composable
fun RepeatPickerSheet(
    visible: Boolean,
    current: RepeatRule,
    currentEndDate: LocalDate?,
    anchorDate: LocalDate,
    onConfirm: (RepeatRule, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    // AppBottomSheet 은 내부적으로 Dialog 를 사용해 Compose Preview (layoutlib) 에서 렌더 안 됨.
    // 실제 sheet body 는 [RepeatPickerSheetContent] 로 분리해 프리뷰 대상은 body 만 그림.
    AppBottomSheet(
        visible = true,
        onDismissRequest = onDismiss,
        fullyExpanded = true,
    ) {
        RepeatPickerSheetContent(
            current = current,
            currentEndDate = currentEndDate,
            anchorDate = anchorDate,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * RepeatPickerSheet 의 body 전용. Dialog 프리뷰 제한 우회로 [RepeatPickerSheet] 에서 분리.
 * `ColumnScope` 안에서만 호출 가능 — 하단 CTA 가 `weight(1f, fill=false)` 로 시트 하단에
 * 붙기 위함. 프리뷰는 Column 을 손수 감싸서 재현.
 */
@Composable
internal fun ColumnScope.RepeatPickerSheetContent(
    current: RepeatRule,
    currentEndDate: LocalDate?,
    anchorDate: LocalDate,
    onConfirm: (RepeatRule, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val font = LocalPretendardFontFamily.current

    // 시트가 다시 열릴 때마다 초기 상태를 draft 값으로 리셋.
    var draftRule by remember(current) { mutableStateOf(current) }
    val defaultEnd = remember(currentEndDate, anchorDate) { currentEndDate ?: anchorDate.plus(1, DateTimeUnit.YEAR) }
    var draftEnd by remember(defaultEnd) { mutableStateOf(defaultEnd) }

    // 상단 nav bar. 취소 = onDismiss. 그랩 핸들은 AppBottomSheet 기본 슬롯이 이미 표시.
    Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "취소",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                style = TextStyle(
                    fontFamily = font, fontSize = 17.sp,
                    color = TextPrimary, lineHeight = 22.sp,
                ),
            )
            Text(
                text = "반복",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = font, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, lineHeight = 22.sp,
                ),
            )
            // 우측 spacer — 취소 라벨과 폭 맞춰 중앙 타이틀 정렬.
            Spacer(Modifier.size(width = 32.dp, height = 22.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            // 5개 옵션.
            RepeatRule.Options.forEach { option ->
                val kind = RepeatKind.of(option)
                val selected = kind == RepeatKind.of(draftRule)
                OptionRow(
                    label = option.label,
                    selected = selected,
                    onClick = {
                        draftRule = kind.toRule(draftRule, anchorDate)
                    },
                )
            }

            // Sub-config (선택된 kind 에 따라 다른 UI).
            when (val rule = draftRule) {
                RepeatRule.None -> Unit
                RepeatRule.Daily -> Unit
                is RepeatRule.Weekly -> {
                    SectionDivider()
                    SubHeader("반복 요일")
                    WeeklyChips(
                        selected = rule.days,
                        onToggle = { day ->
                            val next = if (day in rule.days) rule.days - day else rule.days + day
                            draftRule = RepeatRule.Weekly(next)
                        },
                    )
                }
                is RepeatRule.Monthly -> {
                    SectionDivider()
                    SubHeader("반복 날짜")
                    MonthDayGrid(
                        selected = rule.day,
                        onSelect = { draftRule = RepeatRule.Monthly(it) },
                    )
                }
                is RepeatRule.Yearly -> {
                    SectionDivider()
                    SubHeader("반복 월")
                    YearMonthGrid(
                        selected = rule.month,
                        onSelect = { draftRule = RepeatRule.Yearly(month = it, day = rule.day) },
                    )
                    SubHeader("반복 일")
                    MonthDayGrid(
                        selected = rule.day,
                        onSelect = { draftRule = RepeatRule.Yearly(month = rule.month, day = it) },
                    )
                }
            }

            // 종료 날짜 (rule != None 일 때만 노출; 없음이면 종료일 개념 자체 무의미).
            if (draftRule != RepeatRule.None) {
                SectionDivider()
                SubHeader("종료 날짜")
                EndDateWheels(
                    date = draftEnd,
                    minDate = anchorDate,
                    onChange = { draftEnd = it },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

    ConfirmCta {
        onConfirm(draftRule, if (draftRule == RepeatRule.None) null else draftEnd)
    }
}

/** 옵션 리스트 한 행. 선택된 행은 우측 24dp 체크 아이콘. */
@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = font, fontSize = 17.sp,
                color = TextPrimary, lineHeight = 22.sp,
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

@Composable
private fun SectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .height(1.dp)
            .background(SeparatorGrouped),
    )
}

@Composable
private fun SubHeader(label: String) {
    val font = LocalPretendardFontFamily.current
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(top = 16.dp, bottom = 12.dp),
        style = TextStyle(
            fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = TextSecondary, lineHeight = 18.sp,
        ),
    )
}

/** 요일 chip 7개 (일 · 월 · 화 · 수 · 목 · 금 · 토). 복수 선택. */
@Composable
private fun WeeklyChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val labels = listOf(
        DayOfWeek.SUNDAY to "일",
        DayOfWeek.MONDAY to "월",
        DayOfWeek.TUESDAY to "화",
        DayOfWeek.WEDNESDAY to "수",
        DayOfWeek.THURSDAY to "목",
        DayOfWeek.FRIDAY to "금",
        DayOfWeek.SATURDAY to "토",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEach { (day, label) ->
            RoundChip(
                label = label,
                selected = day in selected,
                onClick = { onToggle(day) },
            )
        }
    }
}

/** 매월 · 매년 일자 grid. 1~31, 7열 × 5행 (마지막 3칸). */
@Composable
private fun MonthDayGrid(selected: Int, onSelect: (Int) -> Unit) {
    val rows = listOf(
        (1..7).toList(),
        (8..14).toList(),
        (15..21).toList(),
        (22..28).toList(),
        (29..31).toList(),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { day ->
                    RectChip(
                        label = day.toString(),
                        selected = day == selected,
                        onClick = { onSelect(day) },
                        widthDp = 42,
                    )
                }
            }
        }
    }
}

/** 매년 월 grid. 1월~12월, 4열 × 3행. */
@Composable
private fun YearMonthGrid(selected: Int, onSelect: (Int) -> Unit) {
    val rows = listOf((1..4).toList(), (5..8).toList(), (9..12).toList())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { month ->
                    RectChip(
                        label = "${month}월",
                        selected = month == selected,
                        onClick = { onSelect(month) },
                        widthDp = 78,
                    )
                }
            }
        }
    }
}

/** 원형 chip — 요일 선택용. 40x40. */
@Composable
private fun RoundChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(TextPrimary)
                else Modifier.background(SurfaceCard).border(1.dp, SeparatorGrouped, CircleShape),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else TextPrimary,
                lineHeight = 20.sp,
            ),
        )
    }
}

/** 사각 chip — 날짜/월 선택용. radius 10. */
@Composable
private fun RectChip(label: String, selected: Boolean, onClick: () -> Unit, widthDp: Int) {
    val font = LocalPretendardFontFamily.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(width = widthDp.dp, height = 40.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(TextPrimary)
                else Modifier.background(SurfaceCard).border(BorderStroke(1.dp, SeparatorGrouped), shape),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else TextPrimary,
                lineHeight = 20.sp,
            ),
        )
    }
}

/** 종료 날짜 wheel picker 3컬럼 (년/월/일). anchor 이후만 선택 가능. */
@Composable
private fun EndDateWheels(date: LocalDate, minDate: LocalDate, onChange: (LocalDate) -> Unit) {
    // 최대 10년 뒤까지 스크롤 가능 — materialize 상한 (1000) 안에서 충분한 범위.
    val maxYear = minDate.year + 10
    var draftYear by remember(date) { mutableStateOf(date.year.coerceIn(minDate.year, maxYear)) }
    var draftMonth by remember(date) { mutableStateOf(date.monthNumber) }
    var draftDay by remember(date) { mutableStateOf(date.dayOfMonth) }

    val years = (minDate.year..maxYear).map { "${it}년" }
    val minMonth = if (draftYear == minDate.year) minDate.monthNumber else 1
    val maxMonth = 12
    val months = (minMonth..maxMonth).map { "${it}월" }
    if (draftMonth < minMonth) draftMonth = minMonth

    val monthCap = daysInMonth(draftYear, draftMonth)
    val minDay = if (draftYear == minDate.year && draftMonth == minDate.monthNumber) minDate.dayOfMonth else 1
    val days = (minDay..monthCap).map { "${it}일" }
    if (draftDay < minDay) draftDay = minDay
    if (draftDay > monthCap) draftDay = monthCap

    // 값 변경 시 상위 콜백. 조합된 LocalDate 로 방출.
    val current = LocalDate(draftYear, draftMonth, draftDay)
    if (current != date) onChange(current)

    PickerSurface {
        WheelPicker(
            items = years,
            selectedIndex = (draftYear - minDate.year).coerceIn(0, years.lastIndex),
            onSelectedChange = { draftYear = minDate.year + it },
            modifier = Modifier.weight(1f),
        )
        WheelPicker(
            items = months,
            selectedIndex = (draftMonth - minMonth).coerceIn(0, months.lastIndex),
            onSelectedChange = { draftMonth = minMonth + it },
            modifier = Modifier.weight(1f),
        )
        WheelPicker(
            items = days,
            selectedIndex = (draftDay - minDay).coerceIn(0, days.lastIndex),
            onSelectedChange = { draftDay = minDay + it },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

/** RepeatRule → 리스트 UI 용 flatten. sealed 인스턴스마다 equality 가 달라 kind 로 매칭. */
private enum class RepeatKind {
    None, Daily, Weekly, Monthly, Yearly;

    /** 이 유형으로 draft 전환. 같은 유형이면 기존 서브값 유지, 아니면 anchor 기준 기본값 생성. */
    fun toRule(current: RepeatRule, anchor: LocalDate): RepeatRule = when (this) {
        None -> RepeatRule.None
        Daily -> RepeatRule.Daily
        Weekly -> if (current is RepeatRule.Weekly) current
        else RepeatRule.Weekly(setOf(anchor.dayOfWeek))
        Monthly -> if (current is RepeatRule.Monthly) current
        else RepeatRule.Monthly(anchor.dayOfMonth)
        Yearly -> if (current is RepeatRule.Yearly) current
        else RepeatRule.Yearly(anchor.monthNumber, anchor.dayOfMonth)
    }

    companion object {
        fun of(rule: RepeatRule): RepeatKind = when (rule) {
            RepeatRule.None -> None
            RepeatRule.Daily -> Daily
            is RepeatRule.Weekly -> Weekly
            is RepeatRule.Monthly -> Monthly
            is RepeatRule.Yearly -> Yearly
        }
    }
}

// ────────── Previews ──────────
// AppBottomSheet 은 Dialog 기반이라 layoutlib 이 렌더 못 함 (AppBottomSheet.kt:106 코멘트 참조).
// 프리뷰용으로는 sheet body 자체 (RepeatPickerSheetContent) 만 카드로 감싸 각 반복 유형별
// UI 상태를 확인한다. 실제 앱에서는 [RepeatPickerSheet] 를 쓴다.

/** 프리뷰 프레임: SurfaceGray 배경 + rounded-top 카드 안에 body 렌더. */
@Composable
private fun RepeatPickerPreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    LinkerTheme {
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
                    .padding(top = 8.dp),
                content = content,
            )
        }
    }
}

private val PreviewAnchor: LocalDate = LocalDate(2026, 3, 15)

@Preview
@Composable
private fun RepeatPickerSheetPreview_None() {
    RepeatPickerPreviewFrame {
        RepeatPickerSheetContent(
            current = RepeatRule.None,
            currentEndDate = null,
            anchorDate = PreviewAnchor,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun RepeatPickerSheetPreview_Daily() {
    RepeatPickerPreviewFrame {
        RepeatPickerSheetContent(
            current = RepeatRule.Daily,
            currentEndDate = LocalDate(2026, 12, 31),
            anchorDate = PreviewAnchor,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun RepeatPickerSheetPreview_Weekly() {
    RepeatPickerPreviewFrame {
        RepeatPickerSheetContent(
            current = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            currentEndDate = LocalDate(2026, 12, 31),
            anchorDate = PreviewAnchor,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun RepeatPickerSheetPreview_Monthly() {
    RepeatPickerPreviewFrame {
        RepeatPickerSheetContent(
            current = RepeatRule.Monthly(day = 15),
            currentEndDate = LocalDate(2026, 12, 31),
            anchorDate = PreviewAnchor,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun RepeatPickerSheetPreview_Yearly() {
    RepeatPickerPreviewFrame {
        RepeatPickerSheetContent(
            current = RepeatRule.Yearly(month = 3, day = 15),
            currentEndDate = LocalDate(2027, 3, 15),
            anchorDate = PreviewAnchor,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}
