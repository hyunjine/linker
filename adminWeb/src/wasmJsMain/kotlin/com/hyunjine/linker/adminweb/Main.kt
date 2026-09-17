package com.hyunjine.linker.adminweb

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hyunjine.linker.adminweb.auth.AdminAuthController
import com.hyunjine.linker.adminweb.data.remote.AdminSupabase
import com.hyunjine.linker.adminweb.nav.AdminNavigator

/**
 * Kotlin/Wasm 진입점.
 *
 * `index.html` 이 로드하는 `adminWeb.js` 가 실행되면 이 함수가 호출된다.
 * `ComposeViewport` 는 지정한 DOM 컨테이너 (`<div id="composeContainer">`) 안에 Compose
 * 렌더 트리를 마운트한다.
 *
 * 부트 순서:
 * 1. [AdminSupabase.warmUp] — supabase-kt lazy 초기화를 강제 트리거해 시크릿 누락 시 조기 실패.
 * 2. [AdminAuthController] 생성 · [AdminAuthController.start] — 세션 관찰 시작. localStorage /
 *    sessionStorage 에 저장된 세션이 있으면 자동 복원 → `Authenticated` 로 흘러 콘솔 라우트.
 * 3. [AdminNavigator] 생성 — UI 트리가 관찰할 라우트 홀더. 초기값은 `Splash` 이며 컨트롤러가
 *    유도한 라우트가 `App` 의 `LaunchedEffect` 를 통해 밀려들어온다.
 * 4. `ComposeViewport` 로 마운트.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    AdminSupabase.warmUp()

    val authController = AdminAuthController().apply { start() }
    val navigator = AdminNavigator()

    ComposeViewport(viewportContainerId = "composeContainer") {
        App(navigator = navigator, authController = authController)
    }
}
