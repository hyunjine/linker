package com.hyunjine.linker.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterQuality
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use

private const val MAX_DIMENSION = 512
private const val JPEG_QUALITY = 85

/**
 * Compose Multiplatform iOS 백엔드는 Skia (skiko) 기반이라 skiko Image API 로 인코딩.
 * 큰 원본은 다운스케일 후 JPEG 로 인코딩 — Storage 5MB 제한 회피.
 */
actual fun ImageBitmap.encodeAvatarJpeg(): ByteArray {
    val src = asSkiaBitmap()
    val longSide = maxOf(src.width, src.height)
    val scaled = if (longSide <= MAX_DIMENSION) src else src.downscale(MAX_DIMENSION)
    return Image.makeFromBitmap(scaled).use { img ->
        img.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)
            ?: error("JPEG 인코딩 실패")
    }.bytes
}

/** aspect 유지하며 긴 변을 [maxDim] 으로 맞춘 새 Skia [Bitmap] 반환. */
private fun Bitmap.downscale(maxDim: Int): Bitmap {
    val ratio = maxDim.toFloat() / maxOf(width, height)
    val newW = (width * ratio).toInt().coerceAtLeast(1)
    val newH = (height * ratio).toInt().coerceAtLeast(1)
    val dst = Bitmap()
    dst.allocN32Pixels(newW, newH)
    val srcImage = Image.makeFromBitmap(this)
    val canvas = org.jetbrains.skia.Canvas(dst)
    canvas.drawImageRect(
        srcImage,
        Rect.makeWH(width.toFloat(), height.toFloat()),
        Rect.makeWH(newW.toFloat(), newH.toFloat()),
        SamplingMode.LINEAR,
        null,
        true,
    )
    srcImage.close()
    return dst
}
