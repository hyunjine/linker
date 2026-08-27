package com.hyunjine.linker.ui.anniversary

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.AppBottomSheet
import com.hyunjine.linker.ui.common.AppSwitch
import com.hyunjine.linker.ui.common.AppTopBar
import com.hyunjine.linker.ui.common.PrimaryButton
import com.hyunjine.linker.ui.common.YearMonthDayPickerSheet
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.OnPrimary
import com.hyunjine.linker.ui.theme.PrimaryBlue
import com.hyunjine.linker.ui.theme.Separator
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** UI 층에서 소비하는 기념일 형태. 리포지토리 Row 를 App.kt 에서 매핑해 넘겨준다. */
data class AnniversaryUi(
    val id: String,
    val title: String,
    val date: LocalDate,
    val repeatYearly: Boolean,
)

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 기념일 목록 화면. 드로어 "기념일 설정" 에서 진입.
 *
 * @param items 전체 목록 (App.kt 에서 리포지토리 조회 후 date 오름차순으로 전달 권장).
 * @param busy 저장/삭제 진행 중 여부. true 면 CTA 비활성.
 * @param onBack 좌상단 back.
 * @param onAdd 저장 시트 확정 콜백 (title · date · repeatYearly).
 * @param onDelete 각 row 의 "삭제" 탭 콜백.
 */
@Composable
fun AnniversariesScreen(
    items: List<AnniversaryUi> = emptyList(),
    busy: Boolean = false,
    onBack: () -> Unit = {},
    onAdd: (title: String, date: LocalDate, repeatYearly: Boolean) -> Unit = { _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
) {
    var showAddSheet by remember { mutableStateOf(false) }

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
            Spacer(Modifier.height(TOP_BAR_HEIGHT))

            if (items.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = items, key = { it.id }) { item ->
                        AnniversaryRow(
                            item = item,
                            onDelete = { if (!busy) onDelete(item.id) },
                        )
                    }
                }
            }
        }

        AppTopBar(
            title = "기념일",
            onBack = onBack,
            trailing = {
                AddButton(onClick = { if (!busy) showAddSheet = true })
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }

    AppBottomSheet(
        visible = showAddSheet,
        onDismissRequest = { showAddSheet = false },
        fullyExpanded = true,
        dragHandle = null,
    ) {
        AddAnniversarySheet(
            busy = busy,
            onCancel = { showAddSheet = false },
            onConfirm = { title, date, repeatYearly ->
                showAddSheet = false
                onAdd(title, date, repeatYearly)
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "아직 기념일이 없어요.\n우측 상단 + 로 추가하세요.",
            style = TextStyle(
                color = TextSecondary,
                fontSize = 15.sp,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun AnniversaryRow(item: AnniversaryUi, onDelete: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatDateWithRepeat(item.date, item.repeatYearly),
                style = TextStyle(
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = font,
                ),
            )
        }
        Text(
            text = "삭제",
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            style = TextStyle(
                color = TextSecondary,
                fontSize = 13.sp,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
            ),
        )
    }
}

/**
 * 기념일 추가 시트. 상단 X/✓ 툴바 + 제목 입력 카드 + 날짜 행 + 매년 반복 스위치 + 저장 버튼.
 */
@Composable
private fun AddAnniversarySheet(
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: (title: String, date: LocalDate, repeatYearly: Boolean) -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var repeatYearly by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val canSave = title.isNotBlank() && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 상단 툴바.
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            CircleTextButton("✕", onCancel, Modifier.align(Alignment.CenterStart))
            Text(
                text = "기념일 추가",
                modifier = Modifier.align(Alignment.Center),
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
        }

        // 제목 카드.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "제목",
                modifier = Modifier.width(72.dp),
                style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
            )
            Box(Modifier.weight(1f)) {
                if (title.isEmpty()) {
                    Text(
                        text = "예: 처음 만난 날",
                        style = TextStyle(color = TextSecondary, fontSize = 17.sp, fontFamily = font),
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
                    singleLine = true,
                    cursorBrush = SolidColor(PrimaryBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(),
                )
            }
        }

        // 날짜 · 매년 반복 카드.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "날짜",
                    style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDate(date),
                    style = TextStyle(color = TextSecondary, fontSize = 17.sp, fontFamily = font),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "›",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                    ),
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Separator))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "매년 반복",
                    style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontFamily = font),
                )
                Spacer(Modifier.weight(1f))
                AppSwitch(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = if (busy) "저장 중…" else "저장",
            onClick = { if (canSave) onConfirm(title.trim(), date, repeatYearly) },
        )
    }

    YearMonthDayPickerSheet(
        visible = showDatePicker,
        date = date,
        minDate = LocalDate(1900, 1, 1),
        maxDate = LocalDate(today().year + 100, 12, 31),
        onConfirm = {
            showDatePicker = false
            date = it
        },
        onCancel = { showDatePicker = false },
    )
}

@Composable
private fun CircleTextButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(SurfaceCard)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = TextStyle(
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
            ),
        )
    }
}

private fun formatDate(d: LocalDate): String =
    "${d.year}. ${d.monthNumber().toString().padStart(2, '0')}. ${d.day.toString().padStart(2, '0')}."

private fun formatDateWithRepeat(d: LocalDate, repeatYearly: Boolean): String {
    val base = formatDate(d)
    return if (repeatYearly) "$base · 매년" else base
}

private fun LocalDate.monthNumber(): Int = month.ordinal + 1

@OptIn(ExperimentalTime::class)
private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

// ────────── Previews ──────────

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AnniversariesScreenPreview_Empty() {
    com.hyunjine.linker.ui.theme.ProvidePretendard {
        AnniversariesScreen(items = emptyList())
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AnniversariesScreenPreview_Filled() {
    com.hyunjine.linker.ui.theme.ProvidePretendard {
        AnniversariesScreen(
            items = listOf(
                AnniversaryUi("1", "처음 만난 날", LocalDate(2024, 3, 14), repeatYearly = true),
                AnniversaryUi("2", "100일", LocalDate(2024, 6, 22), repeatYearly = false),
                AnniversaryUi("3", "1주년", LocalDate(2025, 3, 14), repeatYearly = true),
            ),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AnniversariesScreenPreview_Busy() {
    com.hyunjine.linker.ui.theme.ProvidePretendard {
        AnniversariesScreen(
            items = listOf(
                AnniversaryUi("1", "처음 만난 날", LocalDate(2024, 3, 14), repeatYearly = true),
            ),
            busy = true,
        )
    }
}
