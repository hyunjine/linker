package com.hyunjine.linker.feature.everytime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.SegmentedControl
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.Separator
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_empty_timetable
import org.jetbrains.compose.resources.painterResource

/** Empty state 아이콘 · 텍스트에 쓰이는 muted 회색 톤. DdayEmptyScreen 과 통일. */
private val EmptyMuted = Color(0xFFBDBDC3)

/**
 * 본인/파트너 에브리타임 시간표 조회 라우트 (#306).
 * VM 이 두 탭 상태를 유지하고, 화면은 활성 탭의 timetable 또는 empty state 를 렌더.
 */
@Composable
fun EverytimeTimetableRoute(onBack: () -> Unit) {
    val viewModel: EverytimeTimetableViewModel = viewModel { EverytimeTimetableViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    EverytimeTimetableScreen(
        state = ui,
        onBack = onBack,
        onSelectTab = viewModel::selectTab,
        onSelectSemester = viewModel::selectSemester,
        onSaveMyUrl = viewModel::saveMyIdentifier,
        onDismissSaveError = viewModel::clearUrlSaveError,
    )
}

@Composable
private fun EverytimeTimetableScreen(
    state: EverytimeUiState,
    onBack: () -> Unit,
    onSelectTab: (TimetableOwner) -> Unit,
    onSelectSemester: (SemesterRef) -> Unit,
    onSaveMyUrl: (String) -> Unit,
    onDismissSaveError: () -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }
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
            Spacer(Modifier.height(54.dp))

            // 세그먼트 탭 — 본인/상대방. 닉네임이 비어있으면 폴백 라벨.
            OwnerTabs(state = state, onSelect = onSelectTab)
            Spacer(Modifier.height(16.dp))

            val active = state.activeOwner
            val identifier = state.identifierOf(active)
            val payload = state.tabOf(active)

            when {
                state.loadingProfiles -> LoadingBox()
                identifier.isNullOrBlank() -> EmptyTabContent(
                    owner = active,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 40.dp),
                    onAddUrl = { sheetVisible = true },
                )
                payload.loading && payload.timetable == null -> LoadingBox()
                payload.error != null && payload.timetable == null -> ErrorBox(payload.error)
                payload.timetable != null -> {
                    if (payload.semesters.isNotEmpty()) {
                        SemesterSwitcher(
                            semesters = payload.semesters,
                            activeIdentifier = payload.timetable.identifier,
                            onSelect = onSelectSemester,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    TimetableCard(
                        lectures = payload.timetable.lectures,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = buildCaption(state.nicknameOf(active), payload.timetable.ownerName),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = LocalPretendardFontFamily.current,
                        ),
                    )
                }
            }
        }
        AppTopBar(
            title = "에브리타임 시간표",
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }

    // URL 입력 시트 — empty state 의 "URL 추가하기" 버튼으로 노출. 사용자가 저장하면
    // 즉시 dismiss (optimistic) 하고 VM 은 백그라운드에서 DB update + 본인 tab 재fetch.
    EverytimeUrlSheet(
        visible = sheetVisible,
        initial = state.myIdentifier?.let(EverytimeUrl::buildShareUrl).orEmpty(),
        onDismiss = {
            sheetVisible = false
            onDismissSaveError()
        },
        onConfirm = { raw ->
            sheetVisible = false
            onSaveMyUrl(raw)
        },
    )
}

@Composable
private fun OwnerTabs(state: EverytimeUiState, onSelect: (TimetableOwner) -> Unit) {
    SegmentedControl(
        options = listOf(TimetableOwner.Me, TimetableOwner.Partner),
        selected = state.activeOwner,
        onSelect = onSelect,
        label = { owner ->
            when (owner) {
                TimetableOwner.Me -> state.myNickname.ifBlank { "내 시간표" }
                TimetableOwner.Partner -> state.partnerNickname.ifBlank { "상대방 시간표" }
            }
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private fun buildCaption(fallbackNickname: String, ownerName: String): String {
    val name = ownerName.ifBlank { fallbackNickname.ifBlank { "" } }
    return if (name.isBlank()) "시간표" else "${name}님의 시간표"
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PrimaryBlue)
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(160.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = TextStyle(
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = LocalPretendardFontFamily.current,
            ),
        )
    }
}

// ────────── Empty state ──────────

/**
 * 활성 탭에 identifier 가 없을 때 노출되는 empty state. DdayEmptyScreen 과 동일한 리듬
 * (아이콘 · 제목 · 서브 · CTA) 을 유지해 앱 전체 empty state 톤을 통일.
 *
 * "URL 추가하기" 버튼은 항상 본인 URL 시트로 진입 (본인만 자기 URL 을 등록할 수 있음).
 * 상대방 탭 empty 상태에서도 버튼은 노출되며, 서브 카피가 상대방 URL 이 필요하다는 걸 안내.
 */
@Composable
private fun EmptyTabContent(
    owner: TimetableOwner,
    modifier: Modifier,
    onAddUrl: () -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    val title = when (owner) {
        TimetableOwner.Me -> "아직 시간표가 없어요"
        TimetableOwner.Partner -> "상대방이 아직 등록하지 않았어요"
    }
    val subtitle = when (owner) {
        TimetableOwner.Me -> "URL 을 추가하면 상대방과\n서로의 시간표를 확인할 수 있어요"
        TimetableOwner.Partner -> "상대방이 URL 을 등록하면\n여기서 바로 시간표를 볼 수 있어요"
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_empty_timetable),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            colorFilter = ColorFilter.tint(EmptyMuted),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = TextStyle(
                color = TextPrimary,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = TextSecondary,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryCta(text = "URL 추가하기", onClick = onAddUrl)
    }
}

@Composable
private fun PrimaryCta(text: String, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color.White,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
    }
}

// ────────── Existing timetable rendering (unchanged) ──────────

/**
 * 학기 목록을 칩 리스트로 노출. 응답의 primaryTables 순서를 그대로 사용.
 * 칩 하나에 담긴 [SemesterRef.identifier] 로 활성 상태 판단.
 */
@Composable
private fun SemesterSwitcher(
    semesters: List<SemesterRef>,
    activeIdentifier: String?,
    onSelect: (SemesterRef) -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        semesters.forEach { ref ->
            val active = ref.identifier == activeIdentifier
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (active) Modifier.background(PrimaryBlue)
                        else Modifier
                            .background(SurfaceCard)
                            .border(1.dp, Separator, RoundedCornerShape(18.dp))
                    )
                    .clickable { onSelect(ref) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${ref.year}년 ${ref.semester}학기",
                    style = TextStyle(
                        color = if (active) Color.White else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = font,
                    ),
                )
            }
        }
    }
}

/**
 * 요일 (월~금) × 시간 (09~19) 격자 위에 강의 카드를 절대 좌표로 배치.
 * 폭은 부모의 실제 폭으로부터 계산하고, 높이는 슬롯 × [rowH] 로 고정.
 */
@Composable
private fun TimetableCard(
    lectures: List<Lecture>,
    modifier: Modifier = Modifier,
) {
    val font = LocalPretendardFontFamily.current
    // 시간 범위: 09~19 (강의가 밖에 나가면 클립).
    val slotStart = 108   // 09:00
    val slotEnd = 228     // 19:00
    val slotsPerHour = 12
    val rowH = 42.dp
    val headerH = 32.dp
    val gutter = 30.dp
    val days = listOf("월", "화", "수", "목", "금")
    val cardHeight = headerH + rowH * ((slotEnd - slotStart) / slotsPerHour) + 8.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard),
    ) {
        val colWidth = (maxWidth - gutter) / days.size

        // 요일 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerH)
                .padding(start = gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { day ->
                Box(
                    modifier = Modifier.width(colWidth).height(headerH),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day,
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = font,
                        ),
                    )
                }
            }
        }

        // 헤더 하단 세퍼레이터
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset(y = headerH)
                .height(1.dp)
                .background(Separator.copy(alpha = 0.5f)),
        )

        // 시간 축 (좌측 텍스트)
        for (hour in slotStart / slotsPerHour..slotEnd / slotsPerHour) {
            val y = headerH + rowH * (hour - slotStart / slotsPerHour)
            Text(
                text = hour.toString().padStart(2, '0'),
                modifier = Modifier
                    .offset(x = 6.dp, y = y - 8.dp)
                    .width(gutter - 8.dp),
                style = TextStyle(
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = font,
                ),
            )
            if (hour > slotStart / slotsPerHour) {
                Box(
                    modifier = Modifier
                        .offset(x = gutter, y = y)
                        .width(colWidth * days.size)
                        .height(1.dp)
                        .background(SurfaceGray),
                )
            }
        }

        // 세로 컬럼 구분선
        for (i in 1 until days.size) {
            Box(
                modifier = Modifier
                    .offset(x = gutter + colWidth * i)
                    .padding(top = headerH)
                    .width(1.dp)
                    .height(cardHeight - headerH - 8.dp)
                    .background(SurfaceGray),
            )
        }

        // 강의 카드
        lectures.forEach { lecture ->
            val color = paletteFor(lecture.id)
            lecture.slots.forEach { slot ->
                if (slot.day !in 0..4) return@forEach
                val relStart = (slot.startSlot - slotStart).coerceAtLeast(0)
                val relEnd = (slot.endSlot - slotStart).coerceAtMost(slotEnd - slotStart)
                if (relEnd <= relStart) return@forEach
                val xOffset = gutter + colWidth * slot.day + 2.dp
                val yOffset = headerH + rowH * relStart / slotsPerHour + 2.dp
                val blockH = rowH * (relEnd - relStart) / slotsPerHour - 4.dp
                Column(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(colWidth - 4.dp)
                        .height(blockH)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.bg)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = lecture.name,
                        style = TextStyle(
                            color = color.fg,
                            fontSize = 10.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 2,
                    )
                    val place = slot.place.ifBlank { lecture.defaultPlace }
                    if (place.isNotBlank() && blockH > 44.dp) {
                        Text(
                            text = place,
                            style = TextStyle(
                                color = color.fg.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontFamily = font,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private data class LecturePalette(val bg: Color, val fg: Color)

/**
 * 강의 id 를 팔레트 인덱스로 매핑해 색을 안정적으로 할당. 같은 강의는 항상 같은 색.
 * 파스텔 배경 + 진한 전경으로 시안 톤 유지.
 */
private fun paletteFor(lectureId: String): LecturePalette {
    val palette = listOf(
        LecturePalette(Color(0xFFD6E8FF), Color(0xFF005AB4)), // blue
        LecturePalette(Color(0xFFFFE1C8), Color(0xFFB45A00)), // orange
        LecturePalette(Color(0xFFD3F0DC), Color(0xFF147837)), // green
        LecturePalette(Color(0xFFFFD7E1), Color(0xFFB41937)), // pink
        LecturePalette(Color(0xFFE8D6F7), Color(0xFF692DAF)), // purple
        LecturePalette(Color(0xFFFFF0BE), Color(0xFF8C6E00)), // yellow
    )
    val hash = lectureId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return palette[hash % palette.size]
}
