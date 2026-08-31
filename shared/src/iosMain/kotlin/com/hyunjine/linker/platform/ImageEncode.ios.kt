package com.hyunjine.linker.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Compose Multiplatform iOS 백엔드는 Skia (skiko) 기반이라 skiko Image API 로 그대로 PNG 인코딩.
 * UIImage · NSData 우회 필요 없음.
 */
actual fun ImageBitmap.encodeToPngBytes(): ByteArray {
    val skiaImage = Image.makeFromBitmap(asSkiaBitmap())
    val encoded = skiaImage.encodeToData(EncodedImageFormat.PNG)
        ?: error("PNG 인코딩 실패")
    return encoded.bytes
}
