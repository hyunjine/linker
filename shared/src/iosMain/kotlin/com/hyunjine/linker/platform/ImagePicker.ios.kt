package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberImagePicker(onImage: (ImageBitmap?) -> Unit): () -> Unit {
    val viewController = LocalUIViewController.current
    // 최신 콜백을 참조해서 재구성 후에도 올바른 람다가 호출되도록.
    val callback by rememberUpdatedState(onImage)
    // 델리게이트는 시스템이 강한 참조를 안 가지므로 컴포지션 스코프에서 붙잡아둔다.
    var delegateRef by remember { mutableStateOf<PHPickerViewControllerDelegateProtocol?>(null) }

    return remember {
        {
            val config = PHPickerConfiguration().apply {
                setSelectionLimit(1L)
                setFilter(PHPickerFilter.imagesFilter())
            }
            val picker = PHPickerViewController(configuration = config)

            val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(
                    picker: PHPickerViewController,
                    didFinishPicking: List<*>,
                ) {
                    picker.dismissViewControllerAnimated(true, null)
                    delegateRef = null // 이제 GC 되어도 됨

                    val result = didFinishPicking.firstOrNull() as? PHPickerResult
                    if (result == null) {
                        dispatchOnMain { callback(null) }
                        return
                    }
                    // K/N ObjC interop 상 canLoadObjectOfClass(Class) 매핑이 까다로워
                    // NSData 로 직접 요청. UTI "public.image" 는 모든 이미지 포맷 커버.
                    result.itemProvider.loadDataRepresentationForTypeIdentifier(
                        typeIdentifier = "public.image",
                    ) { data: NSData?, _: NSError? ->
                        val bmp = data?.toImageBitmap()
                        dispatchOnMain { callback(bmp) }
                    }
                }
            }
            delegateRef = delegate
            picker.setDelegate(delegate)
            viewController.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/**
 * 고른 사진 데이터를 [ImageBitmap] 으로 디코딩한다.
 *
 * Skia 디코더는 EXIF `Orientation` 태그를 무시해서, 아이폰 카메라로 세로로 찍은 사진 (데이터는
 * 가로이고 방향만 메타데이터에 있음) 이 눕거나 뒤집힌 채로 들어온다. 그래서 `UIImage` 로 먼저
 * 디코딩하고, 방향이 Up 이 아니면 **다시 그려서 방향을 픽셀에 베이크한 뒤** Skia 에 넘긴다
 * (`UIImage.drawInRect` 는 `imageOrientation` 을 적용해 그린다).
 *
 * `UIImage` 가 디코딩하지 못하는 포맷이면 기존처럼 Skia 로 바로 디코딩한다.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toImageBitmap(): ImageBitmap? {
    val bytes = this.toByteArray()
    if (bytes.isEmpty()) return null
    val normalized = normalizedOrientationBytes() ?: bytes
    return runCatching { SkiaImage.makeFromEncoded(normalized).toComposeImageBitmap() }
        .recoverCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
        .getOrNull()
}

/**
 * EXIF 방향이 적용된 JPEG 바이트. 방향이 이미 Up 이거나 `UIImage` 디코딩에 실패하면 null
 * (호출부가 원본 바이트를 그대로 쓴다).
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.normalizedOrientationBytes(): ByteArray? {
    val image = UIImage.imageWithData(this) ?: return null
    if (image.imageOrientation == UIImageOrientation.UIImageOrientationUp) return null

    val width = image.size.useContents { width }
    val height = image.size.useContents { height }
    if (width <= 0.0 || height <= 0.0) return null

    // scale = 1.0 으로 고정 — 기기 배율을 곱하면 원본보다 2~3배 큰 비트맵이 만들어진다.
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    val redrawn = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    // 여기서 한 번 더 JPEG 로 감싸지만, 이어지는 encodeJpeg 가 어차피 재인코딩하므로
    // 품질 손실은 무시할 수준. 품질 1.0 으로 중간 손실을 최소화한다.
    val data = UIImageJPEGRepresentation(redrawn ?: return null, 1.0) ?: return null
    return data.toByteArray().takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = this.length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, len.toULong())
    }
    return out
}

private inline fun dispatchOnMain(crossinline block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
