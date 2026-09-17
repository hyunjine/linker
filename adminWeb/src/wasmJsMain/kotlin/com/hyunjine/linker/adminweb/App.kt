package com.hyunjine.linker.adminweb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.hyunjine.linker.adminweb.auth.AdminAuthController
import com.hyunjine.linker.adminweb.auth.AdminRoute

/**
 * 관리자 콘솔 루트 컴포저블.
 *
 * #275 부트스트랩 · #278 인증 단계에서는 placeholder 만 렌더한다. 실제 로그인/발송 UI 는
 * #280 이 이 자리에 조립하며, 상단의 [AdminAuthController.route] 를 관찰해 화면을 결정한다.
 *
 * @param authController 부트 시점에 `main()` 이 생성해 넘겨준 인증 상태 홀더. 여기서는 현재
 *   라우트 이름만 렌더링해 UI 통합 지점을 명시.
 */
@Composable
fun App(authController: AdminAuthController) {
    val route: AdminRoute by authController.route.collectAsState()
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5FA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Linker · 관리자 콘솔 (${route.label()})",
                color = Color(0xFF111111),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 디버그 표시용 라벨 — UI 확정 전까지 라우트 상태를 눈으로 확인하기 위한 짧은 문자열. */
private fun AdminRoute.label(): String = when (this) {
    is AdminRoute.Splash -> "부팅 중"
    is AdminRoute.Login -> "로그인 필요"
    is AdminRoute.Console -> "콘솔"
    is AdminRoute.SessionExpired -> "세션 만료"
}
