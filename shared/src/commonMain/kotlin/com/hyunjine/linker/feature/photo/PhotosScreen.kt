package com.hyunjine.linker.feature.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.common.PrimaryButton
import com.hyunjine.linker.designsystem.theme.ChipHolidayBg
import com.hyunjine.linker.designsystem.theme.ChipHolidayText
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.PrimaryBlue
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.LocalDate
import linker.shared.generated.resources.Res
import linker.shared.generated.resources.ic_plus
import org.jetbrains.compose.resources.painterResource

private val TOP_BAR_HEIGHT = 54.dp

/** 썸네일 사이 간격. iOS 사진 앱처럼 화면 끝까지 꽉 채운다 (좌우 여백 없음). */
private val GRID_SPACING = 2.dp

/**
 * 사진첩 화면. 드로워 "사진첩" 에서 진입.
 *
 * 최신 달이 위에 오는 월별 그리드 (3열 정사각형 썸네일) + 우상단 추가 버튼.
 * 커플 미연결 상태에서는 그리드 대신 커플 연결 안내를 보여준다 — 사진은 커플 단위 데이터라
 * 올릴 대상이 없다.
 *
 * @param sections 월별로 묶인 사진. 최신 달이 앞에 오도록 정렬된 상태로 전달.
 * @param coupleLinked 커플 연결 여부.
 * @param uploading 업로드 진행 중 여부 — 상단 진행 배너.
 * @param failed 마지막 업로드 실패 여부 — 상단 "다시 시도" 배너.
 * @param onPhotoClick 썸네일 탭 → 전체 보기.
 * @param onAddClick 우상단 + · 빈 상태 버튼 → 사진 선택 UI.
 */
@Composable
fun PhotosScreen(
    sections: List<PhotoMonthSection> = emptyList(),
    coupleLinked: Boolean = true,
    loading: Boolean = false,
    uploading: Boolean = false,
    failed: Boolean = false,
    onBack: () -> Unit = {},
    onPhotoClick: (photoId: String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onRetryUpload: () -> Unit = {},
    onDismissFailure: () -> Unit = {},
    onCoupleLinkClick: () -> Unit = {},
) {
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

            if (uploading) UploadingBanner()
            if (failed) FailureBanner(onRetry = onRetryUpload, onDismiss = onDismissFailure)

            when {
                !coupleLinked -> CoupleRequiredState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onCoupleLinkClick = onCoupleLinkClick,
                )
                sections.isEmpty() -> EmptyState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    loading = loading,
                    onAddClick = onAddClick,
                )
                else -> PhotoGrid(
                    sections = sections,
                    onPhotoClick = onPhotoClick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        AppTopBar(
            title = "사진첩",
            onBack = onBack,
            trailing = { if (coupleLinked) AddButton(onClick = onAddClick) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun PhotoGrid(
    sections: List<PhotoMonthSection>,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.label}", span = { GridItemSpan(maxLineSpan) }) {
                MonthHeader(label = section.label)
            }
            items(items = section.photos, key = { it.id }) { photo ->
                PhotoImage(
                    path = photo.thumbPath,
                    url = photo.thumbUrl,
                    contentDescription = "${formatPhotoDate(photo.date)} 사진",
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onPhotoClick(photo.id) },
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = TextStyle(
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LocalPretendardFontFamily.current,
        ),
    )
}

@Composable
private fun EmptyState(modifier: Modifier, loading: Boolean, onAddClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 최초 로드 중에는 "사진이 없어요" 가 깜빡이지 않도록 아무 것도 그리지 않는다.
        if (loading) return@Box
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = "아직 사진이 없어요.\n날짜를 정해 첫 사진을 올려보세요.",
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = font,
                ),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "첫 사진 추가", onClick = onAddClick)
        }
    }
}

@Composable
private fun CoupleRequiredState(modifier: Modifier, onCoupleLinkClick: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = "사진첩은 상대방과 연결한 뒤 쓸 수 있어요.\n연결하면 둘이 함께 사진을 모을 수 있어요.",
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = font,
                ),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "상대방 연결", onClick = onCoupleLinkClick)
        }
    }
}

/** 업로드 진행 배너. 전용 progress 컴포넌트가 없어 텍스트 배너로 간단히 표시. */
@Composable
private fun UploadingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryBlue.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "사진을 올리는 중…",
            style = TextStyle(
                color = PrimaryBlue,
                fontSize = 14.sp,
                fontFamily = LocalPretendardFontFamily.current,
            ),
        )
    }
}

@Composable
private fun FailureBanner(onRetry: () -> Unit, onDismiss: () -> Unit) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ChipHolidayBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "사진을 올리지 못했어요.",
            modifier = Modifier.weight(1f),
            style = TextStyle(color = ChipHolidayText, fontSize = 14.sp, fontFamily = font),
        )
        Text(
            text = "다시 시도",
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = TextStyle(
                color = ChipHolidayText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
            ),
        )
        Text(
            text = "✕",
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            style = TextStyle(color = ChipHolidayText, fontSize = 13.sp, fontFamily = font),
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_plus),
            contentDescription = "사진 추가",
            colorFilter = ColorFilter.tint(TextPrimary),
            modifier = Modifier.size(20.dp),
        )
    }
}

// ────────── Previews ──────────

private fun previewPhoto(id: String, date: LocalDate) = PhotoUi(
    id = id,
    date = date,
    thumbPath = "couple/$id-thumb.jpg",
    thumbUrl = null,
    originalPath = "couple/$id.jpg",
    originalUrl = null,
    uploaderLabel = "나",
    deletable = true,
)

@Preview
@Composable
private fun PhotosScreenPreview_Empty() {
    LinkerTheme { PhotosScreen() }
}

@Preview
@Composable
private fun PhotosScreenPreview_Filled() {
    LinkerTheme {
        PhotosScreen(
            sections = listOf(
                PhotoMonthSection("2026년 11월", (1..6).map { previewPhoto("n$it", LocalDate(2026, 11, it)) }),
                PhotoMonthSection("2026년 10월", (1..5).map { previewPhoto("o$it", LocalDate(2026, 10, it)) }),
            ),
        )
    }
}

@Preview
@Composable
private fun PhotosScreenPreview_NoCouple() {
    LinkerTheme { PhotosScreen(coupleLinked = false) }
}

@Preview
@Composable
private fun PhotosScreenPreview_Failed() {
    LinkerTheme {
        PhotosScreen(
            sections = listOf(
                PhotoMonthSection("2026년 11월", listOf(previewPhoto("a", LocalDate(2026, 11, 3)))),
            ),
            failed = true,
        )
    }
}
