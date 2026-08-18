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
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
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

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toImageBitmap(): ImageBitmap? {
    val bytes = this.toByteArray()
    if (bytes.isEmpty()) return null
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
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
