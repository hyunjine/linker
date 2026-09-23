package com.hyunjine.linker.feature.task

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.SegmentedControl
import com.hyunjine.linker.designsystem.theme.DrawerCheckBlue
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.MenuOutlineShadow
import com.hyunjine.linker.designsystem.theme.MenuShadow
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TaskCheckBorder
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_check
import linker.shared.generated.resources.ic_sort
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** 카드 · 메뉴 모서리 (Figma 12). */
private val CardShape = RoundedCornerShape(12.dp)

/** 정렬 버튼과 메뉴 사이 간격. */
private val MenuAnchorGap = 6.dp

/**
 * 메뉴 둘레에 두는 그림자 여백 (blur 24 + 아래 offset 8). AnimatedVisibility 의 scale · fade 는
 * 콘텐츠 크기만 한 레이어에 그린 뒤 합성하므로, 여백 없이는 애니메이션 동안 그림자가 잘려 끝난 뒤에야 보인다.
 */
private val MenuShadowPaddingSide = 24.dp
private val MenuShadowPaddingBottom = 32.dp

/** 정렬 메뉴 폭 · 항목 높이 (Figma Sort Menu). */
private val MenuWidth = 160.dp
private val MenuItemHeight = 44.dp

/**
 * 할 일 내역 화면 (#304 · Figma 4280:85790 / 4280:85830).
 *
 * 구성: 상단바 → 남은 일 / 끝낸 일 세그먼트 → 우측 정렬 버튼 (탭 시 최신순 · 과거순 메뉴) →
 * 흰 카드 한 장 안에 할 일 행. 행 탭은 편집, 체크박스 탭은 완료 토글 (반대 탭으로 이동).
 *
 * @param ui 화면 상태.
 * @param onBack 뒤로가기.
 * @param onSelectTab 세그먼트 탭 선택.
 * @param onSelectSort 정렬 메뉴 항목 선택.
 * @param onToggleDone 체크박스 탭 (할 일 id).
 * @param onTaskClick 행 본문 탭 (할 일 id).
 * @param onRetry 에러 상태 "다시 시도".
 */
@Composable
fun TasksScreen(
    ui: TasksUiState,
    onBack: () -> Unit,
    onSelectTab: (TaskTab) -> Unit,
    onSelectSort: (TaskSort) -> Unit,
    onToggleDone: (String) -> Unit,
    onTaskClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    // 로딩 · 빈 상태를 오가도 스크롤 위치가 유지되도록 리스트 밖에서 소유.
    val listState = rememberLazyListState()
    // 하단 safe area 는 리스트 contentPadding 으로 넘겨 홈 인디케이터 뒤까지 스크롤되게.
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    // 정렬 메뉴. 닫힘 애니메이션이 끝날 때까지 오버레이를 유지해야 해서 Boolean 대신 전이 상태.
    val menuState = remember { MutableTransitionState(false) }
    // 메뉴를 정렬 버튼 바로 아래에 붙이기 위한 좌표 (화면 Box 기준).
    var screenCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var sortButtonCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            .onGloballyPositioned { screenCoords = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            AppTopBar(title = "할 일", onBack = onBack)
            Spacer(Modifier.height(18.dp))
            SegmentedControl(
                options = TaskTab.entries,
                selected = ui.tab,
                onSelect = onSelectTab,
                label = { it.label },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                SortButton(
                    selected = ui.sort,
                    onClick = { menuState.targetState = true },
                    modifier = Modifier.onGloballyPositioned { sortButtonCoords = it },
                )
            }
            Spacer(Modifier.height(7.dp))
            when {
                ui.loading -> LoadingState()
                ui.error != null -> ErrorState(message = ui.error, onRetry = onRetry)
                else -> {
                    val tasks = ui.visibleTasks
                    if (tasks.isEmpty()) {
                        EmptyState(tab = ui.tab)
                    } else {
                        TaskList(
                            tasks = tasks,
                            listState = listState,
                            sort = ui.sort,
                            bottomInset = bottomInset,
                            onToggleDone = onToggleDone,
                            onTaskClick = onTaskClick,
                        )
                    }
                }
            }
        }
        SortMenuOverlay(
            state = menuState,
            selected = ui.sort,
            anchor = {
                val screen = screenCoords
                val button = sortButtonCoords
                if (screen != null && button != null && screen.isAttached && button.isAttached) {
                    screen.localBoundingBoxOf(button)
                } else {
                    null
                }
            },
            onSelect = { sort ->
                menuState.targetState = false
                // LazyColumn 은 기본적으로 첫 보이는 아이템의 key 를 따라가서, 순서가 뒤집히면
                // 그 아이템이 옮겨간 위치 (중간 · 끝) 로 스크롤이 튄다. 정렬 변경은 key 대신
                // 현재 인덱스 · 오프셋을 그대로 유지하도록 다음 measure 에 요청.
                listState.requestScrollToItem(
                    index = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                )
                onSelectSort(sort)
            },
            onDismiss = { menuState.targetState = false },
        )
    }
}

/**
 * 한 장의 카드처럼 보이도록 첫 행은 위 모서리, 마지막 행은 아래 모서리만 둥글린다.
 * 행마다 [Modifier.animateItem] 을 걸어 체크로 빠지는 행이 자연스럽게 사라지게.
 *
 * 정렬 변경은 행이 위아래로 미끄러지는 대신 fade out → fade in 으로 바뀌도록 key 에 [sort] 를 섞는다.
 * key 가 전부 바뀌면 LazyColumn 은 이전 행을 사라지는 항목 (fadeOut), 새 행을 등장 항목 (fadeIn) 으로
 * 처리해 제자리에서 교차 페이드된다. 같은 정렬 안의 체크 토글은 key 가 유지돼 기존 슬라이드 그대로.
 */
@Composable
private fun TaskList(
    tasks: List<TaskItem>,
    listState: LazyListState,
    sort: TaskSort,
    bottomInset: Dp,
    onToggleDone: (String) -> Unit,
    onTaskClick: (String) -> Unit,
) {
    val today = remember { today() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
    ) {
        itemsIndexed(tasks, key = { _, t -> "${sort.name}:${t.id}" }) { index, task ->
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 12.dp else 0.dp,
                topEnd = if (index == 0) 12.dp else 0.dp,
                bottomStart = if (index == tasks.lastIndex) 12.dp else 0.dp,
                bottomEnd = if (index == tasks.lastIndex) 12.dp else 0.dp,
            )
            TaskRow(
                task = task,
                dateLabel = dateLabel(task.date, today),
                onToggleDone = { onToggleDone(task.id) },
                onClick = { onTaskClick(task.id) },
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = tween(220),
                        fadeOutSpec = tween(160),
                    )
                    .clip(shape)
                    .background(SurfaceCard),
            )
        }
    }
}

/** 체크박스 · 제목 · 날짜 한 줄 (Figma 행 높이 62 = 상하 20 + 본문). */
@Composable
private fun TaskRow(
    task: TaskItem,
    dateLabel: String,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noRippleClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TaskCheckbox(checked = task.isDone, onClick = onToggleDone)
        Text(
            text = task.title,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = TextPrimary,
            ),
        )
        Text(
            text = dateLabel,
            style = TextStyle(
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = TextSecondary,
            ),
        )
    }
}

/**
 * Figma 체크박스: 22dp · radius 6. 체크 = 파란 fill + 흰 체크마크, 언체크 = 1.5dp 회색 테두리.
 * 행 탭 (편집) 과 분리된 자체 탭 영역.
 */
@Composable
private fun TaskCheckbox(checked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .then(
                if (checked) Modifier.background(DrawerCheckBlue)
                else Modifier.border(1.5.dp, TaskCheckBorder, shape),
            )
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Image(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = "완료 취소",
                colorFilter = ColorFilter.tint(SurfaceCard),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** "최신순 ≡↓" 버튼. 탭하면 [SortMenuOverlay] 를 연다. */
@Composable
private fun SortButton(selected: TaskSort, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = modifier.noRippleClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = selected.label,
            style = TextStyle(fontFamily = font, fontSize = 12.sp, color = TextPrimary),
        )
        Image(
            painter = painterResource(Res.drawable.ic_sort),
            contentDescription = "정렬",
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 화면 전체를 덮는 정렬 메뉴 레이어. Popup 대신 화면 안 오버레이로 그려서 바깥 탭 한 번 (손가락이
 * 닿는 순간) 에 바로 닫히게 한다. 바깥 탭은 뒤 콘텐츠로 전달하지 않는다 (iOS 메뉴 동작과 동일).
 *
 * @param state 메뉴 표시 전이 상태. targetState 로 열고 닫는다.
 * @param selected 현재 정렬 — 체크 표시.
 * @param anchor 정렬 버튼 bounds (오버레이 좌표계). 메뉴 우측 끝을 버튼에 맞추고 6dp 아래에 붙인다.
 * @param onSelect 항목 선택.
 * @param onDismiss 바깥 탭.
 */
@Composable
private fun SortMenuOverlay(
    state: MutableTransitionState<Boolean>,
    selected: TaskSort,
    anchor: () -> Rect?,
    onSelect: (TaskSort) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.currentState && !state.targetState) return
    if (state.targetState) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        onDismiss()
                    }
                },
        )
    }
    val density = LocalDensity.current
    val gap = with(density) { MenuAnchorGap.roundToPx() }
    val sidePad = with(density) { MenuShadowPaddingSide.roundToPx() }
    // 버튼이 있는 우상단을 기준점으로 커지며 등장 (iOS 풀다운 메뉴 톤).
    // 기준점은 여백 포함 영역이 아닌 메뉴 본체의 우상단 (여백 폭을 전체 크기 비율로 환산).
    val menuHeight = MenuItemHeight * TaskSort.entries.size
    val transformOrigin = TransformOrigin(
        pivotFractionX = (MenuShadowPaddingSide + MenuWidth) / (MenuShadowPaddingSide * 2 + MenuWidth),
        pivotFractionY = MenuShadowPaddingSide / (MenuShadowPaddingSide + menuHeight + MenuShadowPaddingBottom),
    )
    AnimatedVisibility(
        visibleState = state,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val rect = anchor() ?: return@layout
                placeable.place(
                    // 그림자 여백만큼 되돌려 메뉴 본체의 우측 끝 · 상단이 버튼 기준에 맞도록.
                    x = rect.right.roundToInt() - placeable.width + sidePad,
                    y = rect.bottom.roundToInt() + gap - sidePad,
                )
            }
        },
        enter = scaleIn(
            animationSpec = tween(180),
            initialScale = 0.6f,
            transformOrigin = transformOrigin,
        ) + fadeIn(tween(180)),
        exit = scaleOut(
            animationSpec = tween(140),
            targetScale = 0.6f,
            transformOrigin = transformOrigin,
        ) + fadeOut(tween(140)),
    ) {
        SortMenu(selected = selected, onSelect = onSelect)
    }
}

/** iOS 풀다운 메뉴 스타일 정렬 메뉴 (Figma Sort Menu · 폭 160 · 항목 44). 선택 항목은 좌측 체크 + SemiBold. */
@Composable
private fun SortMenu(selected: TaskSort, onSelect: (TaskSort) -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(
        Modifier.padding(
            start = MenuShadowPaddingSide,
            end = MenuShadowPaddingSide,
            top = MenuShadowPaddingSide,
            bottom = MenuShadowPaddingBottom,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(MenuWidth)
                // Figma Sort Menu 그림자 그대로: (0, 8) blur 24 · 12% + 외곽 1px 4% 로 흰 카드 위에서도 경계 유지.
                // elevation 기반 shadow() 는 플랫폼 기본 알파가 곱해져 거의 안 보여서 dropShadow 사용.
                .dropShadow(CardShape, Shadow(radius = 24.dp, color = MenuShadow, offset = DpOffset(0.dp, 8.dp)))
                .dropShadow(CardShape, Shadow(radius = 1.dp, color = MenuOutlineShadow))
                .clip(CardShape)
                .background(SurfaceCard),
        ) {
            TaskSort.entries.forEach { sort ->
                val isSelected = sort == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MenuItemHeight)
                        .noRippleClickable { onSelect(sort) }
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Image(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(TextPrimary),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = sort.label,
                        style = TextStyle(
                            fontFamily = font,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 15.sp,
                            color = TextPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PrimaryBlue)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "할 일을 불러오지 못했어요",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontFamily = font),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "다시 시도",
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PrimaryBlue)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            style = TextStyle(
                color = SurfaceCard,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
            ),
        )
    }
}

@Composable
private fun EmptyState(tab: TaskTab) {
    val font = LocalPretendardFontFamily.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = when (tab) {
                TaskTab.Open -> "남은 할 일이 없어요"
                TaskTab.Done -> "끝낸 할 일이 없어요"
            },
            style = TextStyle(color = TextSecondary, fontSize = 15.sp, fontFamily = font),
        )
    }
}

/**
 * 행 우측 날짜 라벨. 오늘 · 어제 · 내일은 단어로, 올해는 `M.d`, 다른 해는 `yy.M.d`.
 *
 * @param date 할 일 날짜.
 * @param today 기준일.
 */
internal fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.plus(-1, DateTimeUnit.DAY) -> "어제"
    today.plus(1, DateTimeUnit.DAY) -> "내일"
    else -> {
        val md = "${date.month.ordinal + 1}.${date.day}"
        if (date.year == today.year) md else "${(date.year % 100).toString().padStart(2, '0')}.$md"
    }
}

/** 리플 없는 clickable. 체크박스 · 행처럼 자체 시각 피드백이 있거나 iOS 톤을 맞추는 곳용. */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

// ---------- Preview ----------

@Preview
@Composable
private fun TasksScreenPreview() {
    val today = LocalDate(2026, 9, 23)
    LinkerTheme {
        TasksScreen(
            ui = TasksUiState(
                loading = false,
                tasks = listOf(
                    TaskItem("1", "분리수거 배출", today, isDone = false),
                    TaskItem("2", "병원 예약 확정 전화", today.plus(-1, DateTimeUnit.DAY), isDone = false),
                    TaskItem("3", "데이트 코스 짜기", LocalDate(2026, 9, 21), isDone = false),
                    TaskItem("4", "세탁소 픽업", LocalDate(2026, 5, 11), isDone = false),
                    TaskItem("5", "치과 정기검진 예약", LocalDate(2025, 12, 11), isDone = false),
                ),
            ),
            onBack = {},
            onSelectTab = {},
            onSelectSort = {},
            onToggleDone = {},
            onTaskClick = {},
            onRetry = {},
        )
    }
}
