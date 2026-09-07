package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * 시스템 클립보드에 텍스트를 복사하는 트리거를 기억한다. Compose Multiplatform 의
 * [LocalClipboardManager] 를 사용하므로 별도 expect/actual 없이 공통 구현.
 *
 * 대체 API [androidx.compose.ui.platform.LocalClipboard] 는 `setClipEntry` 가 suspend +
 * `ClipEntry` 생성이 플랫폼별이라 commonMain 에서 평문 텍스트 복사 헬퍼를 만들려면 expect/actual
 * 이 필요. 마이그레이션 비용 대비 이득이 작아 당분간 deprecated API 유지.
 *
 * @return 호출 시 [text] 를 클립보드에 복사하는 람다.
 */
@Suppress("DEPRECATION")
@Composable
fun rememberCopyToClipboard(): (text: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) {
        { text -> clipboard.setText(AnnotatedString(text)) }
    }
}
