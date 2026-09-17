package com.hyunjine.linker.adminweb

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/**
 * Kotlin/Wasm 진입점.
 *
 * `index.html` 이 로드하는 `adminWeb.js` 가 실행되면 이 함수가 호출된다.
 * `ComposeViewport` 는 지정한 DOM 컨테이너 (`<div id="composeContainer">`) 안에 Compose
 * 렌더 트리를 마운트한다.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeContainer") {
        App()
    }
}
