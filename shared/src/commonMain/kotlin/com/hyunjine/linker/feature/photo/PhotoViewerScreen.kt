package com.hyunjine.linker.feature.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyunjine.linker.designsystem.common.AppTopBar
import com.hyunjine.linker.designsystem.theme.CardDivider
import com.hyunjine.linker.designsystem.theme.DestructiveRed
import com.hyunjine.linker.designsystem.theme.LinkerTheme
import com.hyunjine.linker.designsystem.theme.LocalPretendardFontFamily
import com.hyunjine.linker.designsystem.theme.SurfaceCard
import com.hyunjine.linker.designsystem.theme.SurfaceGray
import com.hyunjine.linker.designsystem.theme.TextPrimary
import com.hyunjine.linker.designsystem.theme.TextSecondary
import kotlinx.datetime.LocalDate

private val TOP_BAR_HEIGHT = 54.dp

/**
 * 사진 전체 보기. 사진첩 썸네일 · 날짜 상세 가로 목록에서 진입.
 *
 * 사진 · 날짜 · 올린 사람을 보여주고, **내가 올린 사진에만** 삭제 버튼을 노출한다
 * (서버 RLS `couple_photos_delete` 가 실제 권한을 강제하고, UI 는 그에 맞춰 가린다).
 *
 * @param photo 표시할 사진. null 이면 로딩 중.
 * @param notFound 사진을 찾을 수 없음 (이미 지워졌거나 다른 커플 사진).
 */
@Composable
fun PhotoViewerScreen(
    photo: PhotoUi? = null,
    notFound: Boolean = false,
    deleting: Boolean = false,
    deleteFailed: Boolean = false,
    onBack: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val font = LocalPretendardFontFamily.current
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

            when {
                notFound -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text(
                        text = "사진을 찾을 수 없어요.\n이미 삭제되었을 수 있어요.",
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            color = TextSecondary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontFamily = font,
                        ),
                    )
                }

                photo != null -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Spacer(Modifier.height(0.dp))
                    PhotoImage(
                        path = photo.originalPath,
                        url = photo.originalUrl,
                        contentDescription = "${formatPhotoDate(photo.date)} 사진",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    InfoCard(dateText = formatPhotoDate(photo.date), uploader = photo.uploaderLabel)
                    if (deleteFailed) {
                        Text(
                            text = "삭제하지 못했어요. 다시 시도해 주세요.",
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = TextStyle(
                                color = DestructiveRed,
                                fontSize = 13.sp,
                                fontFamily = font,
                            ),
                        )
                    }
                    if (photo.deletable) {
                        DeleteCard(deleting = deleting, onClick = onDelete)
                    }
                }

                else -> Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }

        AppTopBar(
            title = "사진",
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

/** 날짜 · 올린 사람 두 행. 더보기 화면의 카드 리스트와 같은 규격 (행 높이 · 구분선). */
@Composable
private fun InfoCard(dateText: String, uploader: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard),
    ) {
        InfoRow(label = "날짜", value = dateText)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(1.dp)
                .background(CardDivider),
        )
        InfoRow(label = "올린 사람", value = uploader)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val font = LocalPretendardFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
            ),
        )
        Text(
            text = value,
            style = TextStyle(color = TextSecondary, fontSize = 16.sp, fontFamily = font),
        )
    }
}

/** 더보기 화면의 "로그아웃" 행과 같은 모양 — 파괴적 액션은 빨간 텍스트 카드. */
@Composable
private fun DeleteCard(deleting: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(enabled = !deleting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (deleting) "삭제 중…" else "사진 삭제",
            style = TextStyle(
                color = DestructiveRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = LocalPretendardFontFamily.current,
            ),
        )
    }
}

// ────────── Previews ──────────

private val previewPhoto = PhotoUi(
    id = "p1",
    date = LocalDate(2026, 11, 3),
    thumbPath = "couple/p1_thumb.jpg",
    thumbUrl = null,
    originalPath = "couple/p1.jpg",
    originalUrl = null,
    uploaderLabel = "나",
    deletable = true,
)

@Preview
@Composable
private fun PhotoViewerScreenPreview_Mine() {
    LinkerTheme { PhotoViewerScreen(photo = previewPhoto) }
}

@Preview
@Composable
private fun PhotoViewerScreenPreview_Partner() {
    LinkerTheme {
        PhotoViewerScreen(photo = previewPhoto.copy(uploaderLabel = "현진", deletable = false))
    }
}

@Preview
@Composable
private fun PhotoViewerScreenPreview_NotFound() {
    LinkerTheme { PhotoViewerScreen(notFound = true) }
}
