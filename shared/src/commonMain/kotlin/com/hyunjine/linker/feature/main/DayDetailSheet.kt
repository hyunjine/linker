package com.hyunjine.linker.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppBottomSheet
import com.hyunjine.linker.feature.schedule.ScheduleType
import com.hyunjine.linker.designsystem.theme.CalendarLunarText
import com.hyunjine.linker.designsystem.theme.CalendarSaturday
import com.hyunjine.linker.designsystem.theme.CalendarSunday
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.OwnerMeBg
import com.hyunjine.linker.designsystem.theme.OwnerMeText
import com.hyunjine.linker.designsystem.theme.OwnerPartnerBg
import com.hyunjine.linker.designsystem.theme.OwnerPartnerText
import com.hyunjine.linker.designsystem.theme.OwnerUsBg
import com.hyunjine.linker.designsystem.theme.OwnerUsText
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SegmentTrack
import com.hyunjine.linker.designsystem.theme.Separator
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_cal_31
import linker.shared.generated.resources.ic_todo
import org.jetbrains.compose.resources.painterResource

/** 이벤트/할 일의 소유자 태그. Figma 3종: 나 (노랑), 상대방 (분홍), 우리 (보라). */
enum class DayOwner(val label: String, val bg: Color, val fg: Color) {
    Me("나", OwnerMeBg, OwnerMeText),
    Partner("상대방", OwnerPartnerBg, OwnerPartnerText),
    Us("우리", OwnerUsBg, OwnerUsText),
}

/** 체크박스 할 일 한 개. */
data class DayTask(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val owner: DayOwner,
)

/** 시각이 있는 일정 (하루 일정). start/end 는 "오전 10:00" 같은 이미 포맷된 표시 문자열. */
data class TimedSchedule(
    val id: String,
    val startTime: String,
    val endTime: String?,
    val title: String,
    val owner: DayOwner,
)

/** 종일 일정. 좌측 세로바 색상은 사용자 캘린더 색을 그대로 씀 (없으면 null → 세로바 미표시). */
data class AllDaySchedule(
    val id: String,
    val title: String,
    val owner: DayOwner,
    val barColor: Color? = null,
)

/** 날짜 상세 시트의 payload. */
data class DayDetail(
    val date: LocalDate,
    val lunarLabel: String? = null,
    val tasks: List<DayTask> = emptyList(),
    val timedSchedules: List<TimedSchedule> = emptyList(),
    val allDaySchedules: List<AllDaySchedule> = emptyList(),
)

/**
 * 캘린더 셀 탭 시 뜨는 하단 상세 시트. Figma 2693:63060/63255/63484.
 *
 * 구성:
 *  - 상단: 음력 라벨 (있으면) + 큰 날짜 "M.d (요일)"
 *  - 세그먼트 pill 두 개: 할 일 / 일정 (지금은 시각적 라벨, 필터링은 후속)
 *  - 할 일 섹션: 카운트 헤더 + 체크박스 리스트 ([DayTask])
 *  - 하루 일정 섹션: 시각 표기 + 제목 + 소유자 pill
 *  - 종일 일정 섹션: 좌측 세로바 (선택) + 제목 + 소유자 pill
 *
 * [detail] 이 null 이면 아무 것도 렌더하지 않음 (상위에서 visible 을 false 로 유지).
 */
@Composable
fun DayDetailSheet(
    visible: Boolean,
    detail: DayDetail?,
    onDismiss: () -> Unit,
    onToggleTask: (taskId: String) -> Unit = {},
    onSelectTask: (taskId: String) -> Unit = {},
    onAdd: (ScheduleType) -> Unit = {},
    /** 스케줄 (timed / all-day) row 탭 시 편집 화면 진입 콜백. task 는 체크박스만 반응. */
    onSelectSchedule: (scheduleId: String) -> Unit = {},
) {
    AppBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        fullyExpanded = true,
    ) {
        if (detail == null) return@AppBottomSheet
        // fillMaxSize 를 안 주면 ModalBottomSheet 이 콘텐츠 높이만큼만 표시됨. skipPartiallyExpanded
        // 만으로는 partial-expand 스텝만 건너뛸 뿐, 실제 시트 높이가 화면을 채우려면 콘텐츠가 세로를
        // 다 요구해야 함.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            DayHeader(date = detail.date, lunarLabel = detail.lunarLabel)
            Spacer(Modifier.height(12.dp))
            AddChips(onAdd = onAdd)
            Spacer(Modifier.height(20.dp))
            TaskSection(
                tasks = detail.tasks,
                onToggle = onToggleTask,
                onSelect = onSelectTask,
            )
            if (detail.timedSchedules.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                TimedScheduleSection(schedules = detail.timedSchedules, onSelect = onSelectSchedule)
            }
            if (detail.allDaySchedules.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                AllDayScheduleSection(schedules = detail.allDaySchedules, onSelect = onSelectSchedule)
            }
        }
    }
}

// ────────── Header ──────────

@Composable
private fun DayHeader(date: LocalDate, lunarLabel: String?) {
    val pretendard = LocalPretendardFontFamily.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (lunarLabel != null) {
            Text(
                text = lunarLabel,
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = CalendarLunarText,
                ),
            )
            Spacer(Modifier.height(2.dp))
        }
        val dowColor = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> CalendarSunday
            DayOfWeek.SATURDAY -> CalendarSaturday
            else -> TextPrimary
        }
        Text(
            text = "${date.month.ordinal + 1}.${date.day} (${date.dayOfWeek.koreanShort()})",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = dowColor,
            ),
        )
    }
}

private fun DayOfWeek.koreanShort(): String = when (this) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

// ────────── Add chips ──────────

/**
 * Figma 2822:79389 스펙 (AddChips). 헤더 아래 pill 두 개 — 각 chip 은 좌측 아이콘 + 라벨.
 * 카운트는 노출하지 않고, 탭 시 해당 [ScheduleType] 으로 일정 생성 진입 콜백.
 */
@Composable
private fun AddChips(onAdd: (ScheduleType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AddChip(
            iconRes = Res.drawable.ic_todo,
            label = ScheduleType.Task.label,
            onClick = { onAdd(ScheduleType.Task) },
        )
        AddChip(
            iconRes = Res.drawable.ic_cal_31,
            label = ScheduleType.Schedule.label,
            onClick = { onAdd(ScheduleType.Schedule) },
        )
    }
}

@Composable
private fun AddChip(
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    onClick: () -> Unit,
) {
    val pretendard = LocalPretendardFontFamily.current
    // Figma 78dp / 74dp width — 좌 12dp / 우 14dp / 상하 8dp 패딩 + icon 16dp + 6dp gap + text.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(SegmentTrack)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TextPrimary,
            ),
        )
    }
}

// ────────── Task ──────────

@Composable
private fun TaskSection(
    tasks: List<DayTask>,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "할 일 ${tasks.size}")
        if (tasks.isEmpty()) return
        Spacer(Modifier.height(4.dp))
        tasks.forEach { task ->
            TaskRow(
                task = task,
                onToggle = { onToggle(task.id) },
                onSelect = { onSelect(task.id) },
            )
        }
    }
}

@Composable
private fun TaskRow(task: DayTask, onToggle: () -> Unit, onSelect: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    val rowInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val checkInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Row 전체 탭 = 편집 진입. 체크박스만 자체 clickable 로 토글을 가로챔 (아래 Box).
            .clickable(interactionSource = rowInteraction, indication = null, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (task.isDone) PrimaryBlue else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (task.isDone) PrimaryBlue else Separator,
                    shape = RoundedCornerShape(6.dp),
                )
                // 체크박스 탭은 토글 · Row 로 이벤트 안 넘어감. 시각 피드백은 색 변화가 대신하므로 리플 X.
                .clickable(interactionSource = checkInteraction, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (task.isDone) {
                Text(
                    text = "✓",
                    style = TextStyle(
                        fontFamily = pretendard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    ),
                )
            }
        }
        Text(
            text = task.title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (task.isDone) TextSecondary else TextPrimary,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            ),
        )
        OwnerPill(owner = task.owner)
    }
}

// ────────── Timed ──────────

@Composable
private fun TimedScheduleSection(schedules: List<TimedSchedule>, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "하루 일정 ${schedules.size}")
        Spacer(Modifier.height(4.dp))
        schedules.forEach { TimedRow(it, onSelect) }
    }
}

@Composable
private fun TimedRow(schedule: TimedSchedule, onSelect: (String) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(schedule.id) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.width(64.dp)) {
            Text(
                text = schedule.startTime,
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = TextSecondary,
                ),
            )
            if (schedule.endTime != null) {
                Text(
                    text = schedule.endTime,
                    style = TextStyle(
                        fontFamily = pretendard,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    ),
                )
            }
        }
        Text(
            text = schedule.title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextPrimary,
            ),
        )
        OwnerPill(owner = schedule.owner)
    }
}

// ────────── AllDay ──────────

@Composable
private fun AllDayScheduleSection(schedules: List<AllDaySchedule>, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "종일 일정 ${schedules.size}")
        Spacer(Modifier.height(4.dp))
        schedules.forEach { AllDayRow(it, onSelect) }
    }
}

@Composable
private fun AllDayRow(schedule: AllDaySchedule, onSelect: (String) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(schedule.id) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (schedule.barColor != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(schedule.barColor),
            )
        }
        Text(
            text = schedule.title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextPrimary,
            ),
        )
        OwnerPill(owner = schedule.owner)
    }
}

// ────────── Common bits ──────────

@Composable
private fun SectionHeader(text: String) {
    val pretendard = LocalPretendardFontFamily.current
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        style = TextStyle(
            fontFamily = pretendard,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = TextSecondary,
        ),
    )
}

@Composable
private fun OwnerPill(owner: DayOwner) {
    val pretendard = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(owner.bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = owner.label,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = owner.fg,
            ),
        )
    }
}
