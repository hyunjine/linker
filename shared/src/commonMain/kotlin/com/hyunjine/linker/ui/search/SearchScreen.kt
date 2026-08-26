package com.hyunjine.linker.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.ui.common.BackCircleButton
import com.hyunjine.linker.ui.theme.LocalPretendardFontFamily
import com.hyunjine.linker.ui.theme.SurfaceCard
import com.hyunjine.linker.ui.theme.SurfaceGray
import com.hyunjine.linker.ui.theme.TextPrimary
import com.hyunjine.linker.ui.theme.TextSecondary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate

/** 검색 결과 아이템 — 스케줄. 탭 시 편집 화면으로 이동. */
data class SearchScheduleItem(
    val id: String,
    val title: String,
    val date: LocalDate,
    val ownerColor: Color,
)

/** 검색 결과 아이템 — 기념일. 탭 시 기념일 목록으로 이동 (per-item 편집은 후속 이슈). */
data class SearchAnniversaryItem(
    val id: String,
    val title: String,
    val date: LocalDate,
    val repeatYearly: Boolean,
)

data class SearchResults(
    val schedules: List<SearchScheduleItem> = emptyList(),
    val anniversaries: List<SearchAnniversaryItem> = emptyList(),
) {
    val isEmpty: Boolean get() = schedules.isEmpty() && anniversaries.isEmpty()
}

private const val DEBOUNCE_MS = 300L
private val TOP_BAR_HEIGHT = 54.dp

/**
 * 스케줄 · 기념일 통합 검색 화면.
 *
 * - 입력 시 300ms debounce 후 [onSearch] 호출
 * - 빈 문자열은 결과 초기화 (호출 안 함)
 * - 스케줄 탭 → [onScheduleClick], 기념일 탭 → [onAnniversaryClick]
 *
 * @param onSearch 검색 실행자. 리포지토리 조회 후 [SearchResults] 반환. suspend 로 실행됨.
 */
@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSearch: suspend (String) -> SearchResults,
    onScheduleClick: (id: String) -> Unit,
    onAnniversaryClick: (id: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    var busy by remember { mutableStateOf(false) }
    // 사용자 입력 이력. debounce 대상은 query state — snapshotFlow 로 Flow 화.
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MS)
            .collect { q ->
                if (q.isBlank()) {
                    results = SearchResults()
                    busy = false
                    return@collect
                }
                busy = true
                results = runCatching { onSearch(q) }
                    .onFailure { println("[Search] 실패: $it") }
                    .getOrDefault(SearchResults())
                busy = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceGray)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onBack = onBack,
                onClear = { query = "" },
            )
            when {
                query.isBlank() -> HintState()
                busy && results.isEmpty -> LoadingState()
                results.isEmpty -> NoResultsState(query)
                else -> ResultsList(
                    results = results,
                    onScheduleClick = onScheduleClick,
                    onAnniversaryClick = onAnniversaryClick,
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val font = LocalPretendardFontFamily.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackCircleButton(onClick = onBack)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "제목 검색",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontFamily = font,
                    ),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(TextPrimary),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = font,
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = TextStyle(
                        color = TextSecondary,
                        fontSize = 18.sp,
                        fontFamily = font,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HintState() {
    val font = LocalPretendardFontFamily.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "제목으로 스케줄 · 기념일을 찾아보세요",
            style = TextStyle(color = TextSecondary, fontSize = 15.sp, fontFamily = font),
        )
    }
}

@Composable
private fun LoadingState() {
    val font = LocalPretendardFontFamily.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "검색 중…",
            style = TextStyle(color = TextSecondary, fontSize = 15.sp, fontFamily = font),
        )
    }
}

@Composable
private fun NoResultsState(query: String) {
    val font = LocalPretendardFontFamily.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "\"$query\" 결과가 없어요",
            style = TextStyle(color = TextSecondary, fontSize = 15.sp, fontFamily = font),
        )
    }
}

@Composable
private fun ResultsList(
    results: SearchResults,
    onScheduleClick: (id: String) -> Unit,
    onAnniversaryClick: (id: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (results.schedules.isNotEmpty()) {
            item { SectionHeader("스케줄") }
            items(items = results.schedules, key = { "s-${it.id}" }) { item ->
                ScheduleRow(item = item, onClick = { onScheduleClick(item.id) })
            }
        }
        if (results.anniversaries.isNotEmpty()) {
            if (results.schedules.isNotEmpty()) item { Spacer(Modifier.height(4.dp)) }
            item { SectionHeader("기념일") }
            items(items = results.anniversaries, key = { "a-${it.id}" }) { item ->
                AnniversaryRow(item = item, onClick = { onAnniversaryClick(item.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val font = LocalPretendardFontFamily.current
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp),
        style = TextStyle(
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
        ),
    )
}

@Composable
private fun ScheduleRow(item: SearchScheduleItem, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(item.ownerColor),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatDate(item.date),
                style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontFamily = font),
            )
        }
    }
}

@Composable
private fun AnniversaryRow(item: SearchAnniversaryItem, onClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (item.repeatYearly) "${formatDate(item.date)} · 매년 반복" else formatDate(item.date),
                style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontFamily = font),
            )
        }
    }
}

private fun formatDate(date: LocalDate): String {
    val m = date.monthNumber.toString().padStart(2, '0')
    val d = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}. $m. $d"
}
