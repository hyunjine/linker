package com.hyunjine.linker.adminweb

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hyunjine.linker.adminweb.auth.AdminAuthController
import com.hyunjine.linker.adminweb.data.remote.AdminSupabase

/**
 * Kotlin/Wasm 진입점.
 *
 * `index.html` 이 로드하는 `adminWeb.js` 가 실행되면 이 함수가 호출된다.
 * `ComposeViewport` 는 지정한 DOM 컨테이너 (`<div id="composeContainer">`) 안에 Compose
 * 렌더 트리를 마운트한다.
 *
 * #278 부터 여기서 Supabase 클라이언트 [AdminSupabase] 를 warmUp 하고 [AdminAuthController]
 * 관찰을 시작한다. UI (#280) 는 `App` 에서 `authController` 를 소비.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // supabase-kt lazy 초기화를 강제 트리거 → 시크릿 누락 시 조기 실패.
    AdminSupabase.warmUp()

    val authController = AdminAuthController().apply { start() }

    ComposeViewport(viewportContainerId = "composeContainer") {
        App(authController = authController)
    }
}
