package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Android 는 프리뷰 전용이라 실제 사진 선택은 지원하지 않는다.
 * 호출해도 아무 일도 일어나지 않는다.
 */
@Composable
actual fun rememberImagePicker(onImage: (ImageBitmap?) -> Unit): () -> Unit {
    return remember { { /* no-op */ } }
}
