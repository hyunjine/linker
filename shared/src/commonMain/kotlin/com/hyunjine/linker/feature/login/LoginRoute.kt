package com.hyunjine.linker.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.auth.rememberAppleLoginClient
import com.hyunjine.linker.auth.rememberGoogleLoginClient
import com.hyunjine.linker.auth.rememberKakaoLoginClient
import com.hyunjine.linker.auth.supportsAppleSignIn
import com.hyunjine.linker.feature.auth.AuthGateMode
import com.hyunjine.linker.feature.auth.AuthGateScreen
import com.hyunjine.linker.platform.DebugConfig

/**
 * 스플래시 + 로그인 통합 라우트. 실제 시각은 [AuthGateScreen] 이 담당하고, 이 파일은
 * ViewModel · KakaoLoginClient 를 결선하는 라우팅 어댑터 역할만 한다.
 *
 * @param mode 세션 로딩 중이면 [AuthGateMode.Splash], 로그인 필요 상태면 [AuthGateMode.Login].
 * 두 상태 사이 전환은 컴포저블 내부에서 tween.
 */
@Composable
fun LoginRoute(mode: AuthGateMode) {
    val viewModel: LoginViewModel = viewModel { LoginViewModel() }
    val kakao = rememberKakaoLoginClient()
    val apple = rememberAppleLoginClient()
    val google = rememberGoogleLoginClient()
    val debugError by viewModel.debugError.collectAsStateWithLifecycle()
    var debugSheetOpen by remember { mutableStateOf(false) }

    AuthGateScreen(
        mode = mode,
        onKakaoLoginClick = { viewModel.onKakaoLoginClick(kakao) },
        // iOS 만 네이티브 Apple Sign-In 지원. Android 는 supportsAppleSignIn=false 로 버튼 자체 hidden.
        showAppleLogin = supportsAppleSignIn,
        onAppleLoginClick = { viewModel.onAppleLoginClick(apple) },
        // Google 은 양쪽 플랫폼 모두 지원 — 항상 노출.
        onGoogleLoginClick = { viewModel.onGoogleLoginClick(google) },
        // Release 빌드에서는 DebugConfig.enabled = false 라 노출 자체 X.
        showDebugLogin = DebugConfig.enabled,
        onDebugLoginClick = { debugSheetOpen = true },
    )

    if (debugSheetOpen) {
        DebugLoginSheet(
            error = debugError,
            onDismiss = {
                debugSheetOpen = false
                viewModel.clearDebugError()
            },
            onSubmit = { email, password ->
                viewModel.onDebugEmailLogin(email, password) { /* 세션 status flow 가 라우팅 */ }
                debugSheetOpen = false
            },
        )
    }
}
