package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Android 는 프리뷰 전용이라 실제 햅틱은 발생시키지 않는다. */
@Composable
actual fun rememberSelectionHaptic(): () -> Unit = remember { { /* no-op */ } }
