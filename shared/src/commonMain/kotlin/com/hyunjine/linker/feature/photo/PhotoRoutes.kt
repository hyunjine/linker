package com.hyunjine.linker.feature.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 사진첩 화면 라우트. 사진 선택 → 날짜 확인 시트 → 업로드까지 여기서 완결하고,
 * 화면 이동 (전체 보기 · 커플 연결) 만 콜백으로 올린다.
 *
 * @param onPhotoClick 썸네일 탭 → 전체 보기 route (사진 id 전달).
 */
@Composable
fun PhotosRoute(
    onBack: () -> Unit,
    onPhotoClick: (photoId: String) -> Unit,
    onCoupleLinkClick: () -> Unit,
) {
    val viewModel: PhotosViewModel = viewModel { PhotosViewModel() }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val picking = rememberPhotoPicking()

    PhotosScreen(
        sections = ui.sections,
        coupleLinked = ui.coupleLinked,
        loading = ui.loading,
        uploading = ui.uploading,
        failed = ui.failed,
        onBack = onBack,
        onPhotoClick = onPhotoClick,
        onAddClick = picking.launch,
        onRetryUpload = viewModel::retryUpload,
        onDismissFailure = viewModel::dismissFailure,
        onCoupleLinkClick = onCoupleLinkClick,
    )

    // 업로드가 성공할 때까지 시트를 열어 둬서 진행 · 실패 상태를 그 자리에서 보여준다.
    LaunchedEffect(ui.uploadedTick) {
        if (ui.uploadedTick > 0) picking.clear()
    }

    val pending = picking.pending
    PhotoDateConfirmSheet(
        visible = pending != null,
        preview = pending,
        // 사진첩에서 올리면 오늘이 기본값. 날짜 상세에서 올리면 그 날짜로 seed 된다.
        initialDate = today(),
        busy = ui.uploading,
        failed = ui.failed,
        onConfirm = { date ->
            val image = pending ?: return@PhotoDateConfirmSheet
            viewModel.upload(image, date)
        },
        onDismiss = {
            picking.clear()
            viewModel.dismissFailure()
        },
    )
}

/**
 * 사진 전체 보기 라우트. 삭제 성공 시 [onBack] 으로 화면을 닫는다.
 *
 * @param photoId nav 로 전달된 사진 id. VM 이 이 id 로 row · signed URL 을 다시 조회한다.
 */
@Composable
fun PhotoViewerRoute(
    photoId: String,
    onBack: () -> Unit,
) {
    val viewModel: PhotoViewerViewModel = viewModel(key = "photo-$photoId") {
        PhotoViewerViewModel(photoId)
    }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    PhotoViewerScreen(
        photo = ui.photo,
        notFound = ui.notFound,
        deleting = ui.deleting,
        deleteFailed = ui.deleteFailed,
        onBack = onBack,
        onDelete = { viewModel.delete(onDeleted = onBack) },
    )
}

@OptIn(ExperimentalTime::class)
private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
