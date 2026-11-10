package com.hyunjine.linker.feature.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.hyunjine.linker.platform.rememberImagePicker

/**
 * 사진 선택 상태 홀더.
 *
 * @param pending 고른 사진. null 이면 아직 고르지 않았거나 확인 시트를 닫은 상태.
 * @param launch 사진 선택 UI 열기.
 * @param clear 고른 사진 버리기 (확인 시트 취소 · 업로드 완료 후).
 */
class PhotoPicking internal constructor(
    val pending: ImageBitmap?,
    val launch: () -> Unit,
    val clear: () -> Unit,
)

/**
 * 사진첩 · 날짜 상세 두 진입점이 공유하는 사진 선택 흐름.
 * 고른 이미지는 날짜 확인 시트의 미리보기로 쓰이고, 확정 시 그대로 업로드로 넘어간다.
 */
@Composable
fun rememberPhotoPicking(): PhotoPicking {
    var pending by remember { mutableStateOf<ImageBitmap?>(null) }
    val pick = rememberImagePicker { image ->
        if (image != null) pending = image
    }
    return PhotoPicking(
        pending = pending,
        launch = pick,
        clear = { pending = null },
    )
}
