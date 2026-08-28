package com.hyunjine.linker.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyunjine.linker.auth.KakaoLoginClient
import com.hyunjine.linker.auth.signInWithKakao
import kotlinx.coroutines.launch

/** LoginScreen — 카카오 버튼 탭 트리거만 담당. 세션 상태는 앱 최상위 sessionStatus flow 가 반영. */
class LoginViewModel : ViewModel() {

    fun onKakaoLoginClick(client: KakaoLoginClient) {
        println("[Auth] 카카오 버튼 click")
        viewModelScope.launch {
            runCatching { signInWithKakao(client) }
                .onFailure { println("[Auth] signInWithKakao 실패: $it") }
        }
    }
}
