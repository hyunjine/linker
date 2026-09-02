package com.hyunjine.linker.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppSwitch
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.SegmentedControl
import com.hyunjine.linker.designsystem.common.liquidGlass
import com.hyunjine.linker.designsystem.common.TimePickerSheet
import com.hyunjine.linker.designsystem.common.YearMonthDayPickerSheet
import com.hyunjine.linker.designsystem.theme.Chevron
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.OnPrimary
import com.hyunjine.linker.designsystem.theme.PlaceholderText
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SeparatorGrouped
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import com.hyunjine.linker.designsystem.theme.TextTertiary
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 일정 등록 / 수정 화면. Figma 2759:77318 iOS 26 톤.
 *
 * 세그먼트는 `할 일` / `일정` 2 항목만 노출 (Figma 2772:78828). 종일 vs 시간은 `일정` 하위의 종일 스위치.
 * 상단 앱바는 프로젝트 공통 [AppTopBar] 를 재사용하고 `trailing` 슬롯에 저장 텍스트 버튼을 얹는다.
 *
 * 수정 모드 + 편집 가능 (상대방 일정이 아닐 때) 이면 하단에 일정 삭제 카드 노출.
 */
@Composable
fun CreateScheduleScreen(
    initial: ScheduleDraft? = null,
    editing: Boolean = false,
    onBack: () -> Unit = {},
    onSave: (ScheduleDraft) -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val today = remember { todayLocalDate() }
    // rememberSaveable 을 initial 의 identity 로 key. initial 이 바뀌면 (다른 유형 pill 로 재진입 등)
    // 이전 세션 값이 restore 되지 않고 새 seed 로 초기화된다.
    var draft by rememberSaveable(initial, stateSaver = ScheduleDraftSaver) {
        mutableStateOf(initial ?: ScheduleDraft(startDate = today, endDate = today))
    }

    var startDateSheet by remember { mutableStateOf(false) }
    var endDateSheet by remember { mutableStateOf(false) }
    var startTimeSheet by remember { mutableStateOf(false) }
    var endTimeSheet by remember { mutableStateOf(false) }
    var repeatSheet by remember { mutableStateOf(false) }

    // 편집 가능 여부는 "이 화면을 어떤 자격으로 열었나" 로만 정해진다.
    // create 모드에서는 owner 를 자유롭게 지정할 수 있어야 하고, edit 모드에서는 화면 진입 시점의
    // 원본 owner 로 판정한다 — mutating `draft.owner` 를 참조하면 사용자가 세그먼트에서 "상대방" 을
    // 고른 순간 화면 전체가 잠겨 다시 "나/공동" 으로 되돌릴 방법이 없어진다.
    val canEdit = !editing || (initial?.isEditableByCurrentUser ?: true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AppTopBar(
                title = if (editing) "일정 수정" else "일정 추가",
                onBack = onBack,
                trailing = {
                    SaveAction(enabled = canEdit) { onSave(draft) }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TitleCard(
                    title = draft.title,
                    enabled = canEdit,
                    onChange = { draft = draft.copy(title = it) },
                )

                SectionBlock(label = "일정 유형") {
                    SegmentedControl(
                        options = ScheduleType.values().toList(),
                        selected = draft.type,
                        onSelect = { if (canEdit) draft = draft.copy(type = it) },
                        label = { it.label },
                    )
                }

                DateTimeCard(
                    draft = draft,
                    enabled = canEdit,
                    onToggleAllDay = { draft = draft.copy(allDay = it) },
                    onStartDateClick = { if (canEdit) startDateSheet = true },
                    onEndDateClick = { if (canEdit) endDateSheet = true },
                    onStartTimeClick = { if (canEdit) startTimeSheet = true },
                    onEndTimeClick = { if (canEdit) endTimeSheet = true },
                )

                SectionBlock(label = "반복") {
                    Card {
                        RowItem(
                            label = "반복",
                            value = draft.repeat.label,
                            onClick = { if (canEdit) repeatSheet = true },
                            enabled = canEdit,
                        )
                    }
                }

                SectionBlock(label = "일정 주체") {
                    // "상대방" 은 picker 에서 제거 — 파트너에게 일정을 배정할 권한 없음.
                    // 파트너가 만든 스케줄 (기존 owner=Partner) 은 편집 화면에서 canEdit=false 로
                    // 그대로 표시되지만, 사용자가 owner 를 바꿀 수는 없음 (segment 조작 불가).
                    val options = remember { listOf(ScheduleOwner.Me, ScheduleOwner.Us) }
                    SegmentedControl(
                        options = options,
                        // draft.owner 가 Partner 면 (파트너 스케줄 편집 · 읽기 전용) 시각적으로
                        // Me 로 fallback — SegmentedControl 은 options 밖 값이면 아무것도 선택 안 함.
                        selected = if (draft.owner == ScheduleOwner.Partner) ScheduleOwner.Me else draft.owner,
                        // owner=Us 로 바꾸면 비공개 개념이 성립하지 않아 자동으로 isPrivate=false.
                        onSelect = {
                            if (canEdit) {
                                draft = draft.copy(
                                    owner = it,
                                    isPrivate = if (it == ScheduleOwner.Us) false else draft.isPrivate,
                                )
                            }
                        },
                        label = { it.label },
                    )
                }

                // 비공개는 owner=Me 일 때만 의미. Us 로 두면 논리 모순이라 섹션 자체를 숨긴다.
                if (draft.owner == ScheduleOwner.Me) {
                    SectionBlock(label = "공개 범위") {
                        Card {
                            PrivateRow(
                                checked = draft.isPrivate,
                                enabled = canEdit,
                                onChange = { draft = draft.copy(isPrivate = it) },
                            )
                        }
                    }
                }

                if (editing && canEdit) {
                    Card {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onDelete)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "일정 삭제",
                                style = TextStyle(
                                    fontFamily = LocalPretendardFontFamily.current,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFFFF3B30),
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 날짜 시트 — 기존 공통 시트 재사용. 종료일은 시작일 이상 강제.
    YearMonthDayPickerSheet(
        visible = startDateSheet,
        date = draft.startDate,
        minDate = LocalDate(1900, 1, 1),
        maxDate = LocalDate(today.year + 100, 12, 31),
        onConfirm = { picked ->
            startDateSheet = false
            val newEnd = if (draft.endDate < picked) picked else draft.endDate
            draft = draft.copy(startDate = picked, endDate = newEnd)
        },
        onCancel = { startDateSheet = false },
    )
    YearMonthDayPickerSheet(
        visible = endDateSheet,
        date = draft.endDate,
        minDate = draft.startDate,
        maxDate = LocalDate(today.year + 100, 12, 31),
        onConfirm = { picked ->
            endDateSheet = false
            draft = draft.copy(endDate = picked)
        },
        onCancel = { endDateSheet = false },
    )
    TimePickerSheet(
        visible = startTimeSheet,
        time = draft.startTime,
        onConfirm = { picked ->
            startTimeSheet = false
            // 같은 날 일정이고 종료 시각이 새 시작 시각보다 이르면 종료 시각도 함께 밀어준다.
            val currentEnd = draft.endTime
            val bumpedEnd = if (
                draft.startDate == draft.endDate &&
                currentEnd != null &&
                compareHhMm(currentEnd, picked) < 0
            ) picked else currentEnd
            draft = draft.copy(startTime = picked, endTime = bumpedEnd)
        },
        onCancel = { startTimeSheet = false },
    )
    // 같은 날 일정이면 종료 시각은 시작 시각 이상으로만 선택 가능하도록 wheel range 를 잘라 준다.
    // 다른 날이면 아무 시각이나 유효하므로 제약 없음.
    TimePickerSheet(
        visible = endTimeSheet,
        time = draft.endTime,
        minTime = if (draft.startDate == draft.endDate) draft.startTime else null,
        onConfirm = { picked ->
            endTimeSheet = false
            draft = draft.copy(endTime = picked)
        },
        onCancel = { endTimeSheet = false },
    )
    RepeatPickerSheet(
        visible = repeatSheet,
        current = draft.repeat,
        currentEndDate = draft.repeatEndDate,
        anchorDate = draft.startDate,
        onConfirm = { rule, endDate ->
            draft = draft.copy(repeat = rule, repeatEndDate = endDate)
            repeatSheet = false
        },
        onDismiss = { repeatSheet = false },
    )
}

/** `"HH:MM"` 두 문자열의 시각 순서를 비교. 파싱 실패 시 0 반환 (동일 취급). */
private fun compareHhMm(a: String, b: String): Int {
    fun toMinutes(s: String): Int? {
        val p = s.split(':')
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
    val am = toMinutes(a) ?: return 0
    val bm = toMinutes(b) ?: return 0
    return am.compareTo(bm)
}

// ────────── Building blocks ──────────

@Composable
private fun SaveAction(enabled: Boolean, onClick: () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    // iOS 26 primary tinted 리퀴드 글래스 pill. `Modifier.liquidGlass` 의 fill 만 PrimaryBlue 로 교체 —
    // 흰색 rim 하이라이트는 그대로 유지해 back circle 과 톤을 맞춤.
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .liquidGlass(shape = CircleShape, fill = SolidColor(PrimaryBlue))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "저장",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = OnPrimary,
            ),
        )
    }
}

@Composable
private fun TitleCard(title: String, enabled: Boolean, onChange: (String) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Card {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            BasicTextField(
                value = title,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp,
                    color = TextPrimary,
                ),
                cursorBrush = SolidColor(PrimaryBlue),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text(
                            text = "제목을 입력하세요",
                            style = TextStyle(
                                fontFamily = pretendard,
                                fontWeight = FontWeight.Normal,
                                fontSize = 17.sp,
                                color = PlaceholderText,
                            ),
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun DateTimeCard(
    draft: ScheduleDraft,
    enabled: Boolean,
    onToggleAllDay: (Boolean) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
) {
    Card {
        if (draft.showsAllDayToggle) {
            AllDayRow(checked = draft.allDay, enabled = enabled, onChange = onToggleAllDay)
            Divider()
        }
        RowItem("시작일", value = formatDate(draft.startDate), onClick = onStartDateClick, enabled = enabled)
        Divider()
        RowItem("종료일", value = formatDate(draft.endDate), onClick = onEndDateClick, enabled = enabled)
        if (draft.showsTimeRows) {
            Divider()
            RowItem("시작 시각", value = formatTime(draft.startTime), onClick = onStartTimeClick, enabled = enabled)
            Divider()
            RowItem("종료 시각", value = formatTime(draft.endTime), onClick = onEndTimeClick, enabled = enabled)
        }
    }
}

/**
 * 비공개 스위치 행. 라벨 아래 짧은 부제로 의미를 알림.
 * RLS 가 파트너의 SELECT 를 아예 차단하므로 UI 에는 별도 필터 로직 없음.
 */
@Composable
private fun PrivateRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "비공개",
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp,
                    color = TextPrimary,
                ),
            )
            Text(
                text = "나만 볼 수 있어요",
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                ),
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AllDayRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "종일",
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                color = TextPrimary,
            ),
        )
        AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SectionBlock(label: String, content: @Composable () -> Unit) {
    val pretendard = LocalPretendardFontFamily.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TextTertiary,
            ),
        )
        content()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard),
    ) {
        content()
    }
}

@Composable
private fun RowItem(label: String, value: String, onClick: () -> Unit, enabled: Boolean) {
    val pretendard = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = pretendard,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                color = TextPrimary,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp,
                    color = TextSecondary,
                ),
            )
            Text(
                text = "›",
                style = TextStyle(
                    fontFamily = pretendard,
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp,
                    color = Chevron,
                ),
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(SeparatorGrouped),
    )
}

// ────────── Helpers ──────────

@OptIn(ExperimentalTime::class)
private fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun formatDate(d: LocalDate): String = "${d.year}. ${d.month.ordinal + 1}. ${d.day}."

private fun formatTime(t: String?): String {
    if (t.isNullOrBlank()) return "미설정"
    val parts = t.split(':')
    if (parts.size != 2) return t
    val h = parts[0].toIntOrNull() ?: return t
    val m = parts[1].toIntOrNull() ?: return t
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$ampm $h12:${m.toString().padStart(2, '0')}"
}

// rememberSaveable Saver
private val ScheduleDraftSaver = androidx.compose.runtime.saveable.Saver<ScheduleDraft, List<Any?>>(
    save = { d ->
        listOf(
            d.title,
            d.startDate.toString(),
            d.endDate.toString(),
            d.type.name,
            d.allDay,
            d.startTime,
            d.endTime,
            when (d.repeat) {
                RepeatRule.None -> "none"
                RepeatRule.Daily -> "daily"
                is RepeatRule.Weekly -> "weekly"
                is RepeatRule.Monthly -> "monthly:${d.repeat.day}"
                is RepeatRule.Yearly -> "yearly:${d.repeat.month}:${d.repeat.day}"
            },
            d.repeatEndDate?.toString(),
            d.owner.name,
            d.isPrivate,
        )
    },
    restore = { list ->
        ScheduleDraft(
            title = list[0] as String,
            startDate = LocalDate.parse(list[1] as String),
            endDate = LocalDate.parse(list[2] as String),
            type = ScheduleType.valueOf(list[3] as String),
            allDay = list[4] as Boolean,
            startTime = list[5] as String?,
            endTime = list[6] as String?,
            repeat = parseRepeat(list[7] as String),
            repeatEndDate = (list[8] as String?)?.let { LocalDate.parse(it) },
            owner = ScheduleOwner.valueOf(list[9] as String),
            isPrivate = (list.getOrNull(10) as? Boolean) ?: false,
        )
    },
)

private fun parseRepeat(s: String): RepeatRule = when {
    s == "none" -> RepeatRule.None
    s == "daily" -> RepeatRule.Daily
    s == "weekly" -> RepeatRule.Weekly(emptySet())
    s.startsWith("monthly:") -> RepeatRule.Monthly(s.substringAfter(":").toIntOrNull() ?: 1)
    s.startsWith("yearly:") -> {
        val parts = s.substringAfter(":").split(":")
        RepeatRule.Yearly(parts.getOrNull(0)?.toIntOrNull() ?: 1, parts.getOrNull(1)?.toIntOrNull() ?: 1)
    }
    else -> RepeatRule.None
}
