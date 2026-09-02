package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIActivityViewController

/**
 * iOS 공유 시트 — `UIActivityViewController` 를 현재 UIViewController 위에 present.
 */
@Composable
actual fun rememberShareText(): (text: String) -> Unit {
    val viewController = LocalUIViewController.current
    return remember(viewController) {
        { text ->
            val activity = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            viewController.presentViewController(activity, animated = true, completion = null)
        }
    }
}
