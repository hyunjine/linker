package com.hyunjine.linker.feature.login

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyunjine.linker.auth.rememberKakaoLoginClient

@Composable
fun LoginRoute() {
    val viewModel: LoginViewModel = viewModel { LoginViewModel() }
    val kakao = rememberKakaoLoginClient()
    LoginScreen(
        onKakaoLoginClick = { viewModel.onKakaoLoginClick(kakao) },
    )
}
