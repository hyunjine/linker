package com.hyunjine.linker.platform

import androidx.compose.runtime.Composable

/**
 * 시스템 공유 시트 (Android `Intent.ACTION_SEND` · iOS `UIActivityViewController`) 를 여는
 * 트리거를 기억한다. 각 플랫폼 actual 이 실제 시트를 띄운다.
 *
 * @return 호출 시 [text] 를 payload 로 공유 시트를 여는 람다.
 */
@Composable
expect fun rememberShareText(): (text: String) -> Unit
