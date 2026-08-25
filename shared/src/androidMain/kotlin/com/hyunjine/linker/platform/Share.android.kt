package com.hyunjine.linker.platform

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android 공유 시트 — `Intent.ACTION_SEND` chooser. Compose 를 호스팅하는 Activity 컨텍스트에서
 * 시작한다. Application 컨텍스트라도 안전하도록 `FLAG_ACTIVITY_NEW_TASK` 를 설정.
 */
@Composable
actual fun rememberShareText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
