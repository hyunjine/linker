package com.hyunjine.linker.platform

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

private const val MAX_DIMENSION = 512
private const val JPEG_QUALITY = 85

actual fun ImageBitmap.encodeAvatarJpeg(): ByteArray {
    val src = asAndroidBitmap()
    val scaled = src.downscaleIfNeeded(MAX_DIMENSION)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    if (scaled !== src) scaled.recycle()
    return out.toByteArray()
}

/** 긴 변을 [maxDim] 으로 맞추고 짧은 변은 aspect 유지. 이미 작으면 원본 반환 (복사 없음). */
private fun Bitmap.downscaleIfNeeded(maxDim: Int): Bitmap {
    val longSide = maxOf(width, height)
    if (longSide <= maxDim) return this
    val ratio = maxDim.toFloat() / longSide
    val newW = (width * ratio).toInt().coerceAtLeast(1)
    val newH = (height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newW, newH, /* filter = */ true)
}
