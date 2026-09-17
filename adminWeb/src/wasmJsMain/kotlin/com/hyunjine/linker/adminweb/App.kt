package com.hyunjine.linker.adminweb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyunjine.linker.adminweb.auth.AdminAuthController
import com.hyunjine.linker.adminweb.auth.AdminRoute
import com.hyunjine.linker.adminweb.auth.LoginReason
import com.hyunjine.linker.adminweb.console.ConsolePlaceholder
import com.hyunjine.linker.adminweb.login.LoginScreen
import com.hyunjine.linker.adminweb.nav.AdminNavigator
import com.hyunjine.linker.adminweb.ui.AdminColors

/**
 * 관리자 콘솔 루트 컴포저블.
 *
 * [navigator] 가 노출하는 현재 라우트를 관찰해 로그인 화면 · 콘솔 화면 · 스플래시를 스왑한다.
 * 라우트의 실제 source of truth 는 [authController] 의 세션 상태이며, [App] 는 그 값을
 * [AdminNavigator.sync] 로 밀어넣어 UI 트리와 정합화한다.
 *
 * @param navigator UI 트리에서 관찰할 라우트를 노출하는 초경량 라우터.
 * @param authController 세션 · 로그인 액션을 담당하는 인증 컨트롤러.
 */
@Composable
fun App(
    navigator: AdminNavigator,
    authController: AdminAuthController,
) {
    val controllerRoute: AdminRoute by authController.route.collectAsState()

    // 컨트롤러가 세션 관찰로 유도한 라우트를 네비게이터에 흘려보낸다. UI 가 로그인 성공 직후
    // 임의로 goToConsole 을 호출해 오버라이드해도, 다음 세션 이벤트가 도착하면 자연 정합화.
    LaunchedEffect(controllerRoute) {
        navigator.sync(controllerRoute)
    }

    val current: AdminRoute by navigator.current

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AdminColors.Background),
        ) {
            when (val route = current) {
                is AdminRoute.Splash -> Splash()
                is AdminRoute.Login -> LoginScreen(
                    controller = authController,
                    initialReason = route.reason,
                    onLoginSuccess = { uid -> navigator.goToConsole(uid) },
                )
                is AdminRoute.Console -> ConsolePlaceholder()
                is AdminRoute.SessionExpired -> LoginScreen(
                    controller = authController,
                    initialReason = LoginReason.None,
                    onLoginSuccess = { uid -> navigator.goToConsole(uid) },
                )
            }
        }
    }
}

/**
 * 세션 복원이 완료되기 전까지 매우 짧게 노출되는 스플래시.
 *
 * 별도 브랜딩 없이 중앙 스피너만 표시 — Compose 렌더 → 세션 관찰 → 첫 라우트 emit 까지의
 * 짧은 gap 을 자연스럽게 채우기 위한 최소 화면이다.
 */
@Composable
private fun Splash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = AdminColors.BrandBlue,
            strokeWidth = 3.dp,
        )
    }
}
