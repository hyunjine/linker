package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * 시스템 클립보드에 텍스트를 복사하는 트리거를 기억한다. Compose Multiplatform 의
 * [LocalClipboardManager] 를 사용하므로 별도 expect/actual 없이 공통 구현.
 *
 * @return 호출 시 [text] 를 클립보드에 복사하는 람다.
 */
@Composable
fun rememberCopyToClipboard(): (text: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) {
        { text -> clipboard.setText(AnnotatedString(text)) }
    }
}
