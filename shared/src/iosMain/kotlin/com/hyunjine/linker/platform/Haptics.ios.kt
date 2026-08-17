package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
actual fun rememberSelectionHaptic(): () -> Unit {
    // 인스턴스를 remember 로 유지 + prepare() 로 예열해 첫 호출 지연을 줄인다.
    val generator = remember {
        UISelectionFeedbackGenerator().also { it.prepare() }
    }
    return remember(generator) {
        { generator.selectionChanged() }
    }
}
