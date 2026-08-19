package com.hyunjine.linker.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.AppBottomSheet
import com.hyunjine.linker.ui.theme.CalendarLunarText
import com.hyunjine.linker.ui.theme.CalendarSaturday
import com.hyunjine.linker.ui.theme.CalendarSunday
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.OwnerMeBg
import com.hyunjine.linker.ui.theme.OwnerMeText
import com.hyunjine.linker.ui.theme.OwnerPartnerBg
import com.hyunjine.linker.ui.theme.OwnerPartnerText
import com.hyunjine.linker.ui.theme.OwnerUsBg
import com.hyunjine.linker.ui.theme.OwnerUsText
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.SegmentTrack
import com.hyunjine.linker.ui.theme.Separator
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

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
) {
    AppBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        fullyExpanded = true,
    ) {
        if (detail == null) return@AppBottomSheet
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            DayHeader(date = detail.date, lunarLabel = detail.lunarLabel)
            Spacer(Modifier.height(12.dp))
            SegmentPills(taskCount = detail.tasks.size, scheduleCount = detail.timedSchedules.size + detail.allDaySchedules.size)
            Spacer(Modifier.height(20.dp))
            TaskSection(tasks = detail.tasks, onToggle = onToggleTask)
            if (detail.timedSchedules.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                TimedScheduleSection(schedules = detail.timedSchedules)
            }
            if (detail.allDaySchedules.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                AllDayScheduleSection(schedules = detail.allDaySchedules)
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

// ────────── Segment ──────────

@Composable
private fun SegmentPills(taskCount: Int, scheduleCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentPill(text = "할 일 $taskCount", selected = true)
        SegmentPill(text = "일정 $scheduleCount", selected = false)
    }
}

@Composable
private fun SegmentPill(text: String, selected: Boolean) {
    val pretendard = LocalPretendardFontFamily.current
    val bg = if (selected) SegmentTrack else Color.Transparent
    val border = if (selected) Color.Transparent else Separator
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
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
private fun TaskSection(tasks: List<DayTask>, onToggle: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "할 일 ${tasks.size}")
        if (tasks.isEmpty()) return
        Spacer(Modifier.height(4.dp))
        tasks.forEach { task ->
            TaskRow(task = task, onToggle = { onToggle(task.id) })
        }
    }
}

@Composable
private fun TaskRow(task: DayTask, onToggle: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ripple 은 체크박스 안에서만. clip → clickable 순으로 두어야 ripple 이 rounded rect 로 잘림.
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggle)
                .background(if (task.isDone) PrimaryBlue else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (task.isDone) PrimaryBlue else Separator,
                    shape = RoundedCornerShape(6.dp),
                ),
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
                color = TextPrimary,
            ),
        )
        OwnerPill(owner = task.owner)
    }
}

// ────────── Timed ──────────

@Composable
private fun TimedScheduleSection(schedules: List<TimedSchedule>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "하루 일정 ${schedules.size}")
        Spacer(Modifier.height(4.dp))
        schedules.forEach { TimedRow(it) }
    }
}

@Composable
private fun TimedRow(schedule: TimedSchedule) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
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
private fun AllDayScheduleSection(schedules: List<AllDaySchedule>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = "종일 일정 ${schedules.size}")
        Spacer(Modifier.height(4.dp))
        schedules.forEach { AllDayRow(it) }
    }
}

@Composable
private fun AllDayRow(schedule: AllDaySchedule) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
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
