package com.hyunjine.linker.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.auth.rememberKakaoLoginClient
import com.hyunjine.linker.platform.DebugConfig

@Composable
fun LoginRoute() {
    val viewModel: LoginViewModel = viewModel { LoginViewModel() }
    val kakao = rememberKakaoLoginClient()
    val debugError by viewModel.debugError.collectAsStateWithLifecycle()
    LoginScreen(
        onKakaoLoginClick = { viewModel.onKakaoLoginClick(kakao) },
        // Release 빌드에서는 DebugConfig.enabled 가 false 라 UI 자체가 안 뜸.
        showDebugLogin = DebugConfig.enabled,
        debugError = debugError,
        onDebugLoginSubmit = { email, password ->
            viewModel.onDebugEmailLogin(email, password) { /* 세션 status flow 가 라우팅 */ }
        },
        onDebugErrorDismiss = viewModel::clearDebugError,
    )
}
